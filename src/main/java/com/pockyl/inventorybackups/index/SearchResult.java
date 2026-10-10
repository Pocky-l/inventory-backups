package com.pockyl.inventorybackups.index;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The answer to a search: the matching items, who holds or held them, and how they moved between players.
 *
 * @param truncated whether lists were cut to the result limit
 */
public record SearchResult(SearchQuery query, List<MatchedItem> items, List<Holder> holders, List<TrailLink> trail,
                           boolean truncated) {
    /**
     * @param key    index key, {@code i:<id>} or {@code f:<hex>}
     * @param sample NBT of one such stack, for its icon and tooltip
     */
    public record MatchedItem(String key, String itemId, @Nullable String name, @Nullable CompoundTag sample) {
        @Nullable
        public String fingerprint() {
            return key.startsWith(ItemKeys.FINGERPRINT_PREFIX) ? key.substring(ItemKeys.FINGERPRINT_PREFIX.length()) : null;
        }
    }

    /**
     * @param count  how many the player holds now (0 when they no longer do)
     * @param ranges {@code from, to} pairs, {@code to} is {@link Long#MAX_VALUE} while held
     */
    public record Holder(UUID player, String name, int count, int max, long first, long last, long[] ranges) {
        public boolean holdsNow() {
            return count > 0;
        }
    }

    public enum LinkKind {
        /** Dropped by one player, picked up by the other. */
        DROP,
        /** Dropped on death, picked up by the other player. */
        DEATH_LOOT,
        /** Put into a container by one player, taken out by the other. */
        CONTAINER,
        /** Left one player and appeared at a nearby one moments later, with no drop or container explaining it. */
        PROBABLE;

        public boolean exact() {
            return this != PROBABLE;
        }
    }

    /**
     * @param from         player the items left, null when unknown
     * @param container    block id for {@link LinkKind#CONTAINER} links
     * @param gapMillis    for probable links: time between leaving and appearing
     */
    public record TrailLink(long time, LinkKind kind, @Nullable UUID from, String fromName, UUID to, String toName, int count,
                            String itemId, String dimension, BlockPos pos, @Nullable String container, @Nullable BlockPos containerPos,
                            long gapMillis) {
    }
}
