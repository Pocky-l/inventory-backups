package com.pockyl.inventorybackups.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import com.pockyl.inventorybackups.InventoryBackups;

/**
 * Turns stacks into pool items and back. Pool items are stored with a count of one, so stacks that only differ in count
 * share one pool item. Items are the NBT of {@link ItemStack#save}; the registries are not needed for that on 1.20.1, the
 * parameter keeps the callers the same as on newer versions.
 */
public final class ItemCodec {
    private ItemCodec() {
    }

    public static LogFile.PoolItem encode(HolderLookup.Provider registries, ItemStack stack) {
        CompoundTag tag = stack.copyWithCount(1).save(new CompoundTag());
        return new LogFile.PoolItem(NbtHash.hash(tag), tag);
    }

    /** The stack of a pool item with the given count; empty when the item no longer exists (a removed mod). */
    public static ItemStack decode(HolderLookup.Provider registries, CompoundTag tag, int count) {
        try {
            ItemStack stack = ItemStack.of(tag);
            if (!stack.isEmpty()) {
                stack.setCount(count);
            }
            return stack;
        } catch (RuntimeException e) {
            InventoryBackups.LOGGER.warn("Skipping an unreadable stored item: {}", e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    /** Registry id of the stored item, read without decoding the stack. */
    public static String itemId(CompoundTag tag) {
        return tag.getString("id");
    }
}
