package com.pockyl.inventorybackups.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

/**
 * The extra slots one mod adds to players. Slots are named by a local name that stays the same while the slot exists,
 * such as {@code ring/1}; {@link ModSlots} prefixes it with {@link #id()}. Implementations reach their mod through
 * reflection, so this mod neither compiles against nor needs any of them.
 */
public interface SlotProvider {
    /** Prefix of the slot keys, also the id of the mod in the admin screen. */
    String id();

    /** Whether the mod is installed and its slots are not already covered by another provider. */
    boolean available();

    /** Reports every slot of the player, empty ones included, with the live stack (not a copy). */
    void read(Player player, BiConsumer<String, ItemStack> slots) throws ReflectiveOperationException;

    /** The live stack in one slot; empty when the slot does not exist. */
    ItemStack get(Player player, String slot) throws ReflectiveOperationException;

    /**
     * Puts a stack into a slot, replacing what is there.
     *
     * @return false when the slot does not exist or does not take the stack; nothing changed then
     */
    boolean set(Player player, String slot, ItemStack stack) throws ReflectiveOperationException;

    /** Whether an admin may put the stack into the slot in the live editor; true unless the mod would refuse it. */
    default boolean accepts(Player player, String slot, ItemStack stack) {
        return true;
    }

    /** Splits a local name {@code <type>/<index>} into its parts; null when it does not have that form. */
    static IndexedSlot indexed(String slot) {
        int slash = slot.lastIndexOf('/');
        if (slash <= 0) {
            return null;
        }
        try {
            return new IndexedSlot(slot.substring(0, slash), Integer.parseInt(slot.substring(slash + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    record IndexedSlot(String type, int index) {
    }
}
