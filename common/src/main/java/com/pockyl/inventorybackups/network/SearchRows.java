package com.pockyl.inventorybackups.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.index.SearchResult;

import java.util.UUID;

/**
 * Search results as sent to the client.
 */
public final class SearchRows {
    private SearchRows() {
    }

    /**
     * @param fingerprint hex fingerprint for distinctive stacks, empty for item ids
     */
    public record ItemRow(String itemId, String fingerprint, String name, ItemStack sample) {
        public static ItemRow read(RegistryFriendlyByteBuf buf) {
            return new ItemRow(NetCodecs.readString(buf), NetCodecs.readString(buf), NetCodecs.readString(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }

        public static void write(RegistryFriendlyByteBuf buf, ItemRow row) {
            NetCodecs.writeString(buf, row.itemId);
            NetCodecs.writeString(buf, row.fingerprint);
            NetCodecs.writeString(buf, row.name);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, row.sample);
        }
    }

    public record HolderRow(UUID player, String name, int count, int max, long first, long last, long[] ranges) {
        private static final int MAX_RANGES = 128;

        public boolean holdsNow() {
            return count > 0;
        }

        public static HolderRow of(SearchResult.Holder holder) {
            return new HolderRow(holder.player(), holder.name(), holder.count(), holder.max(), holder.first(), holder.last(),
                    holder.ranges());
        }

        public static HolderRow read(RegistryFriendlyByteBuf buf) {
            UUID player = buf.readUUID();
            String name = NetCodecs.readString(buf);
            int count = buf.readVarInt();
            int max = buf.readVarInt();
            long first = buf.readLong();
            long last = buf.readLong();
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_RANGES * 2) {
                throw new IllegalArgumentException("Too many ranges: " + size);
            }
            long[] ranges = new long[size];
            for (int i = 0; i < size; i++) {
                ranges[i] = buf.readLong();
            }
            return new HolderRow(player, name, count, max, first, last, ranges);
        }

        public static void write(RegistryFriendlyByteBuf buf, HolderRow row) {
            buf.writeUUID(row.player);
            NetCodecs.writeString(buf, row.name);
            buf.writeVarInt(row.count);
            buf.writeVarInt(row.max);
            buf.writeLong(row.first);
            buf.writeLong(row.last);
            int size = Math.min(row.ranges.length, MAX_RANGES * 2) & ~1;
            buf.writeVarInt(size);
            for (int i = 0; i < size; i++) {
                buf.writeLong(row.ranges[row.ranges.length - size + i]);
            }
        }
    }

    public record LinkRow(long time, SearchResult.LinkKind kind, @Nullable UUID from, String fromName, UUID to, String toName, int count,
                          String dimension, BlockPos pos, String container, @Nullable BlockPos containerPos, long gapMillis) {
        public static LinkRow of(SearchResult.TrailLink link) {
            return new LinkRow(link.time(), link.kind(), link.from(), link.fromName(), link.to(), link.toName(), link.count(),
                    link.dimension(), link.pos(), link.container() == null ? "" : link.container(), link.containerPos(), link.gapMillis());
        }

        public static LinkRow read(RegistryFriendlyByteBuf buf) {
            return new LinkRow(buf.readLong(), buf.readEnum(SearchResult.LinkKind.class), NetCodecs.readOptionalUuid(buf),
                    NetCodecs.readString(buf), buf.readUUID(), NetCodecs.readString(buf), buf.readVarInt(), NetCodecs.readString(buf),
                    buf.readBlockPos(), NetCodecs.readString(buf), NetCodecs.readOptionalPos(buf), buf.readLong());
        }

        public static void write(RegistryFriendlyByteBuf buf, LinkRow row) {
            buf.writeLong(row.time);
            buf.writeEnum(row.kind);
            NetCodecs.writeOptionalUuid(buf, row.from);
            NetCodecs.writeString(buf, row.fromName);
            buf.writeUUID(row.to);
            NetCodecs.writeString(buf, row.toName);
            buf.writeVarInt(row.count);
            NetCodecs.writeString(buf, row.dimension);
            buf.writeBlockPos(row.pos);
            NetCodecs.writeString(buf, row.container);
            NetCodecs.writeOptionalPos(buf, row.containerPos);
            buf.writeLong(row.gapMillis);
        }
    }
}
