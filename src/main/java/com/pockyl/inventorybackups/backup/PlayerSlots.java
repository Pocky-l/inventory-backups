package com.pockyl.inventorybackups.backup;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.pockyl.inventorybackups.compat.CuriosCompat;

/**
 * Reads and writes the slots a state covers, see {@link Slots} for the numbering.
 */
public final class PlayerSlots {
    private PlayerSlots() {
    }

    /** The live stack in a slot, not a copy; empty for slots the player does not have. */
    public static ItemStack get(ServerPlayer player, int slot) {
        int vanilla = Slots.vanillaIndex(slot);
        if (vanilla >= 0) {
            return player.getInventory().getItem(vanilla);
        }
        if (Slots.isEnderChest(slot)) {
            return player.getEnderChestInventory().getItem(slot - Slots.ENDER_START);
        }
        return ItemStack.EMPTY;
    }

    /** Copies of every non-empty slot. */
    public static Int2ObjectMap<ItemStack> collect(ServerPlayer player, boolean enderChest) {
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
        slots.putAll(CuriosCompat.collect(player));
        return slots;
    }

    /**
     * Replaces whole sections with a stored state: the inventory (main, armor, offhand and, when the state has any,
     * curios) and/or the ender chest. A state without curios leaves the curio slots alone, as in the 2.x releases.
     */
    public static void restore(ServerPlayer player, Int2ObjectMap<ItemStack> items, boolean inventory, boolean enderChest) {
        if (inventory) {
            Inventory target = player.getInventory();
            for (int i = 0; i < Slots.MAIN_SIZE + Slots.ARMOR_SIZE + 1; i++) {
                target.setItem(i, copyOf(items.get(Slots.fromVanillaIndex(i))));
            }
            Int2ObjectMap<ItemStack> curios = new Int2ObjectOpenHashMap<>();
            items.int2ObjectEntrySet().forEach(e -> {
                if (Slots.isCurio(e.getIntKey())) {
                    curios.put(e.getIntKey(), e.getValue());
                }
            });
            if (!curios.isEmpty()) {
                CuriosCompat.restore(player, curios);
            }
        }
        if (enderChest) {
            SimpleContainer ender = player.getEnderChestInventory();
            for (int i = 0; i < Math.min(ender.getContainerSize(), Slots.ENDER_SIZE); i++) {
                ender.setItem(i, copyOf(items.get(Slots.ENDER_START + i)));
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    /** Puts the stored stacks back into only the given slots; slots empty in the state become empty. */
    public static void restoreSlots(ServerPlayer player, Int2ObjectMap<ItemStack> items, IntCollection slots) {
        Int2ObjectMap<ItemStack> curios = new Int2ObjectOpenHashMap<>();
        for (int slot : slots) {
            int vanilla = Slots.vanillaIndex(slot);
            if (vanilla >= 0) {
                player.getInventory().setItem(vanilla, copyOf(items.get(slot)));
            } else if (Slots.isEnderChest(slot)) {
                player.getEnderChestInventory().setItem(slot - Slots.ENDER_START, copyOf(items.get(slot)));
            } else if (Slots.isCurio(slot)) {
                curios.put(slot, copyOf(items.get(slot)));
            }
        }
        if (!curios.isEmpty()) {
            CuriosCompat.restoreSlots(player, curios);
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static ItemStack copyOf(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    private static void putIfPresent(Int2ObjectMap<ItemStack> slots, int index, ItemStack stack) {
        if (!stack.isEmpty()) {
            slots.put(index, stack.copy());
        }
    }
}
