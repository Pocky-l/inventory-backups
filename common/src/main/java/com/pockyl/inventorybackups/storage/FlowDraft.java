package com.pockyl.inventorybackups.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A {@link Flow} as the main thread hands it over: a private copy of the stack, encoded later on the storage thread.
 */
public record FlowDraft(long time, FlowType type, UUID player, String playerName, ItemStack stack, String dimension, BlockPos pos,
                        @Nullable UUID source, @Nullable String sourceName, @Nullable String container, @Nullable BlockPos containerPos) {
}
