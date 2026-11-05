package com.pockyl.inventorybackups.storage;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A stored state ready to show or restore: the entry, the decoded stacks after it and the stacks of the entry before it,
 * for diffs.
 *
 * @param previous entry before {@code entry}, null for the oldest stored entry
 */
public record StateView(Entry entry, Int2ObjectMap<ItemStack> items, @Nullable Entry previous,
                        Int2ObjectMap<ItemStack> previousItems) {
}
