package com.pockyl.inventorybackups.storage;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;

import com.pockyl.inventorybackups.InventoryBackups;

/**
 * Turns stacks into pool items and back. Pool items are stored with a count of one, so stacks that only differ in count
 * share one pool item.
 * <p>
 * Pool items carry the data version they were saved with, so backups made on an older Minecraft are brought up to date
 * by Minecraft's data fixers when they are read. Items without one were saved by the 1.21.1 versions of the mod.
 */
public final class ItemCodec {
    private static final String DATA_VERSION = "DataVersion";
    /** Data version of Minecraft 1.21.1, for items saved without one. */
    private static final int UNVERSIONED = 3955;

    private ItemCodec() {
    }

    public static LogFile.PoolItem encode(HolderLookup.Provider registries, ItemStack stack) {
        Tag saved = ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack.copyWithCount(1))
                .getOrThrow();
        CompoundTag tag = saved instanceof CompoundTag compound ? compound : new CompoundTag();
        // The hash covers the item alone, not the version it was saved with.
        long hash = NbtHash.hash(tag);
        NbtUtils.addCurrentDataVersion(tag);
        return new LogFile.PoolItem(hash, tag);
    }

    /** The stack of a pool item with the given count; empty when the item no longer exists (a removed mod). */
    public static ItemStack decode(HolderLookup.Provider registries, CompoundTag tag, int count) {
        try {
            ItemStack stack = parse(registries, tag);
            if (!stack.isEmpty()) {
                stack.setCount(count);
            }
            return stack;
        } catch (RuntimeException e) {
            InventoryBackups.LOGGER.warn("Skipping an unreadable stored item: {}", e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    /** A stack from its saved form, upgraded to this Minecraft version first; empty when it cannot be read. */
    public static ItemStack parse(HolderLookup.Provider registries, CompoundTag tag) {
        return ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), upgrade(tag))
                .resultOrPartial(error -> InventoryBackups.LOGGER.warn("Skipping an unreadable stored item: {}", error))
                .orElse(ItemStack.EMPTY);
    }

    /**
     * The saved item in the format of this Minecraft version, without its data version.
     *
     * @param tag a saved item; not changed
     */
    public static CompoundTag upgrade(CompoundTag tag) {
        int version = NbtUtils.getDataVersion(tag, UNVERSIONED);
        int current = SharedConstants.getCurrentVersion().dataVersion().version();
        CompoundTag item = tag.copy();
        item.remove(DATA_VERSION);
        if (version >= current) {
            return item;
        }
        try {
            Tag fixed = DataFixers.getDataFixer().update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, item), version, current)
                    .getValue();
            return fixed instanceof CompoundTag compound ? compound : item;
        } catch (RuntimeException e) {
            InventoryBackups.LOGGER.warn("Could not upgrade a stored item from data version {}: {}", version, e.getMessage());
            return item;
        }
    }

    /** Registry id of the stored item, read without decoding the stack. */
    public static String itemId(CompoundTag tag) {
        return tag.getStringOr("id", "");
    }
}
