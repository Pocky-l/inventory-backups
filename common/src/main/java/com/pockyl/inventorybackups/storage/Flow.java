package com.pockyl.inventorybackups.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One movement of items between a player and the world: a drop, a pickup, or a container session result.
 *
 * @param hash         content hash of the item without count, see {@link ItemCodec}
 * @param item         the item without count
 * @param source       for pickups: the player who dropped the items, when known
 * @param container    block or entity id of the container for {@link FlowType#PUT} and {@link FlowType#TAKE}
 * @param containerPos position of that container
 */
public record Flow(long time, FlowType type, UUID player, String playerName, long hash, CompoundTag item, int count, String dimension,
                   BlockPos pos, @Nullable UUID source, @Nullable String sourceName, @Nullable String container,
                   @Nullable BlockPos containerPos) {
    /** Same container: same dimension and position. */
    public boolean sameContainer(Flow other) {
        return containerPos != null && containerPos.equals(other.containerPos) && dimension.equals(other.dimension);
    }
}
