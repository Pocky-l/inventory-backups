package com.pockyl.inventorybackups.storage;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * What the main thread hands to the storage thread: private copies of the stacks that changed.
 *
 * @param full    whether {@code changes} lists every non-empty slot of the covered sections (join, imports)
 * @param ender   whether the ender chest is covered by a full capture; when not, the stored ender chest slots stay
 * @param changes copies of the changed stacks by slot; {@link ItemStack#EMPTY} for slots that became empty
 */
public record Capture(UUID player, String name, long time, Trigger trigger, EntryContext context, boolean full, boolean ender,
                      Int2ObjectMap<ItemStack> changes) {
}
