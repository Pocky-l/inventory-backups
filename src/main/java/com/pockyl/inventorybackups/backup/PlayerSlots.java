package com.pockyl.inventorybackups.backup;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.pockyl.inventorybackups.compat.CuriosCompat;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads and writes the slots a backup covers. Inventory slot indices (part of the backup file format): 0-35 main
 * inventory, 100-103 armor (feet to head), -106 offhand, 1000+ Curios. Ender chest slots use their own index 0-26.
 */
public final class PlayerSlots {
    public static final int ARMOR_START = 100;
    public static final int OFFHAND = -106;
    /** Size of the vanilla part of {@link Inventory}: 36 main slots, 4 armor slots and the offhand, in this order. */
    public static final int VANILLA_SLOTS = 41;

    private PlayerSlots() {
    }

    public static Map<Integer, ItemStack> collect(BackupKind kind, ServerPlayer player) {
        Map<Integer, ItemStack> slots = new HashMap<>();
        if (kind == BackupKind.ENDER_CHEST) {
            SimpleContainer enderChest = player.getEnderChestInventory();
            for (int i = 0; i < enderChest.getContainerSize(); i++) {
                putIfPresent(slots, i, enderChest.getItem(i));
            }
            return slots;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            putIfPresent(slots, i, inventory.items.get(i));
        }
        for (int i = 0; i < inventory.armor.size(); i++) {
            putIfPresent(slots, ARMOR_START + i, inventory.armor.get(i));
        }
        putIfPresent(slots, OFFHAND, inventory.offhand.get(0));
        slots.putAll(CuriosCompat.collect(player));
        return slots;
    }

    /** Replaces the player's inventory or ender chest with the given slots. */
    public static void restore(BackupKind kind, ServerPlayer player, Map<Integer, ItemStack> slots) {
        if (kind == BackupKind.ENDER_CHEST) {
            SimpleContainer enderChest = player.getEnderChestInventory();
            enderChest.clearContent();
            slots.forEach((index, stack) -> {
                if (index >= 0 && index < enderChest.getContainerSize()) {
                    enderChest.setItem(index, stack.copy());
                }
            });
            return;
        }
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        Map<Integer, ItemStack> curios = new HashMap<>();
        slots.forEach((index, stack) -> {
            int slot = vanillaSlot(index);
            if (slot >= 0) {
                inventory.setItem(slot, stack.copy());
            } else if (CuriosCompat.isCurioSlot(index)) {
                curios.put(index, stack);
            }
        });
        // Backups without curios leave the curio slots alone, as in the 2.0 releases.
        if (!curios.isEmpty()) {
            CuriosCompat.restore(player, curios);
        }
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * Position of a backup slot in a chest screen: the vanilla inventory layout (see {@link #VANILLA_SLOTS}), then
     * curios. Returns -1 for slots that do not fit into {@code size}.
     */
    public static int screenSlot(BackupKind kind, int index, int size) {
        int slot;
        if (kind == BackupKind.ENDER_CHEST) {
            slot = index;
        } else if (CuriosCompat.isCurioSlot(index)) {
            slot = VANILLA_SLOTS + index - CuriosCompat.FIRST_SLOT;
        } else {
            slot = vanillaSlot(index);
        }
        return slot >= 0 && slot < size ? slot : -1;
    }

    /** Index in {@link Inventory#getItem(int)} for a backup slot, or -1 for curios and unknown slots. */
    private static int vanillaSlot(int index) {
        if (index >= 0 && index < 36) {
            return index;
        }
        if (index >= ARMOR_START && index < ARMOR_START + 4) {
            return 36 + index - ARMOR_START;
        }
        return index == OFFHAND ? 40 : -1;
    }

    private static void putIfPresent(Map<Integer, ItemStack> slots, int index, ItemStack stack) {
        if (!stack.isEmpty()) {
            slots.put(index, stack.copy());
        }
    }
}
