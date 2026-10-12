package com.pockyl.inventorybackups.backup;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.compat.ModSlots;
import com.pockyl.inventorybackups.storage.SlotKeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the slots a state covers, see {@link Slots} for the numbering.
 */
public final class PlayerSlots {
    private PlayerSlots() {
    }

    /**
     * The live stacks (not copies) of every non-empty slot: inventory, armor, offhand, the ender chest when asked for,
     * and the slots of other mods when {@code keys} is given, numbering new mod slots on the way.
     */
    public static Int2ObjectMap<ItemStack> live(ServerPlayer player, boolean enderChest, @Nullable SlotKeys keys) {
        Int2ObjectMap<ItemStack> slots = new Int2ObjectOpenHashMap<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < Slots.MAIN_SIZE + Slots.ARMOR_SIZE + 1; i++) {
            putIfPresent(slots, Slots.fromVanillaIndex(i), inventory.getItem(i));
        }
        if (enderChest) {
            SimpleContainer ender = player.getEnderChestInventory();
            for (int i = 0; i < Math.min(ender.getContainerSize(), Slots.ENDER_SIZE); i++) {
                putIfPresent(slots, Slots.ENDER_START + i, ender.getItem(i));
            }
        }
        if (keys != null) {
            slots.putAll(modSlots(player, keys));
        }
        return slots;
    }

    /** The live stacks of the non-empty mod slots, by number; new slots get their number here. */
    public static Int2ObjectMap<ItemStack> modSlots(ServerPlayer player, SlotKeys keys) {
        Int2ObjectMap<ItemStack> slots = new Int2ObjectOpenHashMap<>();
        if (!ModSlots.any()) {
            return slots;
        }
        for (Map.Entry<String, ItemStack> slot : ModSlots.read(player).entrySet()) {
            if (!slot.getValue().isEmpty()) {
                int number = keys.number(slot.getKey());
                if (number >= 0) {
                    slots.put(number, slot.getValue());
                }
            }
        }
        return slots;
    }

    /**
     * Replaces whole sections with a stored state: the inventory (main, armor, offhand and the mod slots) and/or the
     * ender chest. A state without any mod slot leaves the mod slots alone, as in the 2.x releases. Stacks whose slot
     * no longer exists (a mod removed or a slot type shrank) go into the inventory, or drop at the player's feet.
     *
     * @return what fit nowhere, see {@link #give}
     */
    public static List<ItemStack> restore(ServerPlayer player, Int2ObjectMap<ItemStack> items, boolean inventory, boolean enderChest,
                                          @Nullable SlotKeys keys) {
        List<ItemStack> homeless = new ArrayList<>();
        if (inventory) {
            Inventory target = player.getInventory();
            for (int i = 0; i < Slots.MAIN_SIZE + Slots.ARMOR_SIZE + 1; i++) {
                target.setItem(i, copyOf(items.get(Slots.fromVanillaIndex(i))));
            }
            Int2ObjectMap<ItemStack> extras = new Int2ObjectOpenHashMap<>();
            items.int2ObjectEntrySet().forEach(e -> {
                if (Slots.isExtra(e.getIntKey())) {
                    extras.put(e.getIntKey(), e.getValue());
                }
            });
            if (!extras.isEmpty()) {
                Map<String, ItemStack> wanted = resolve(player, extras, keys, homeless);
                for (Map.Entry<String, ItemStack> slot : ModSlots.read(player).entrySet()) {
                    ItemStack stack = wanted.remove(slot.getKey());
                    if (stack == null && slot.getValue().isEmpty()) {
                        continue;
                    }
                    putModSlot(player, slot.getKey(), stack, homeless);
                }
                homeless.addAll(wanted.values());
            }
        }
        if (enderChest) {
            SimpleContainer ender = player.getEnderChestInventory();
            for (int i = 0; i < Math.min(ender.getContainerSize(), Slots.ENDER_SIZE); i++) {
                ender.setItem(i, copyOf(items.get(Slots.ENDER_START + i)));
            }
        }
        player.inventoryMenu.broadcastChanges();
        return give(player, homeless);
    }

    /** Puts the stored stacks back into only the given slots; slots empty in the state become empty. */
    public static List<ItemStack> restoreSlots(ServerPlayer player, Int2ObjectMap<ItemStack> items, IntCollection slots,
                                               @Nullable SlotKeys keys) {
        List<ItemStack> homeless = new ArrayList<>();
        Int2ObjectMap<ItemStack> extras = new Int2ObjectOpenHashMap<>();
        for (int slot : slots) {
            int vanilla = Slots.vanillaIndex(slot);
            if (vanilla >= 0) {
                player.getInventory().setItem(vanilla, copyOf(items.get(slot)));
            } else if (Slots.isEnderChest(slot)) {
                player.getEnderChestInventory().setItem(slot - Slots.ENDER_START, copyOf(items.get(slot)));
            } else if (Slots.isExtra(slot)) {
                extras.put(slot, items.getOrDefault(slot, ItemStack.EMPTY));
            }
        }
        if (!extras.isEmpty()) {
            List<ItemStack> unresolved = new ArrayList<>();
            Map<String, ItemStack> wanted = resolve(player, extras, keys, unresolved);
            wanted.forEach((key, stack) -> putModSlot(player, key, stack, homeless));
            unresolved.stream().filter(stack -> !stack.isEmpty()).forEach(homeless::add);
        }
        player.inventoryMenu.broadcastChanges();
        return give(player, homeless);
    }

    /**
     * Gives copies of stacks to a player; what does not fit drops at their feet. An offline player is not in the world,
     * so for them the rest goes into their ender chest.
     *
     * @return what fits nowhere (only for offline players)
     */
    public static List<ItemStack> give(ServerPlayer receiver, Iterable<ItemStack> stacks) {
        List<ItemStack> left = new ArrayList<>();
        boolean offline = OfflinePlayers.isOffline(receiver);
        for (ItemStack stack : stacks) {
            // add() shrinks the stack by what fits; the rest drops.
            ItemStack remaining = stack.copy();
            if (receiver.getInventory().add(remaining) || remaining.isEmpty()) {
                continue;
            }
            if (!offline) {
                receiver.drop(remaining, false);
                continue;
            }
            remaining = receiver.getEnderChestInventory().addItem(remaining);
            if (!remaining.isEmpty()) {
                left.add(remaining);
            }
        }
        return left;
    }

    /**
     * Turns stored mod slot numbers into the keys of the player's current slots. Stacks of unknown numbers, and 2.x
     * curios beyond the player's current curio slots, go to {@code homeless}.
     */
    private static Map<String, ItemStack> resolve(ServerPlayer player, Int2ObjectMap<ItemStack> extras, @Nullable SlotKeys keys,
                                                  List<ItemStack> homeless) {
        Map<String, ItemStack> wanted = new HashMap<>();
        List<String> legacy = null;
        for (Int2ObjectMap.Entry<ItemStack> slot : extras.int2ObjectEntrySet()) {
            String key = null;
            if (Slots.isModSlot(slot.getIntKey())) {
                key = keys == null ? null : keys.key(slot.getIntKey());
            } else {
                if (legacy == null) {
                    legacy = ModSlots.legacyCurioKeys(player);
                }
                int index = slot.getIntKey() - Slots.CURIOS_START;
                key = index < legacy.size() ? legacy.get(index) : null;
            }
            if (key == null) {
                homeless.add(slot.getValue());
            } else {
                wanted.put(key, slot.getValue());
            }
        }
        return wanted;
    }

    private static void putModSlot(ServerPlayer player, String key, @Nullable ItemStack stack, List<ItemStack> homeless) {
        ItemStack target = copyOf(stack);
        ItemStack current = ModSlots.get(player, key);
        if (ItemStack.matches(current, target)) {
            return;
        }
        if (!ModSlots.set(player, key, target)) {
            // The slot refused: keep what is there and hand the stored stack over instead.
            InventoryBackups.LOGGER.info("Slot {} of {} did not take the restored item, it goes into the inventory", key,
                    player.getScoreboardName());
            if (!target.isEmpty()) {
                homeless.add(target);
            }
        }
    }

    private static ItemStack copyOf(@Nullable ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    private static void putIfPresent(Int2ObjectMap<ItemStack> slots, int index, ItemStack stack) {
        if (!stack.isEmpty()) {
            slots.put(index, stack);
        }
    }
}
