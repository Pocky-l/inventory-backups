package com.pockyl.inventorybackups.network;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Every payload of the admin screen. Requests carry an id the client picks; the response repeats it, so the client can
 * drop answers to requests it no longer cares about (the admin already scrubbed further).
 */
public final class Payloads {
    /** Most slots an action may name. */
    public static final int MAX_SLOTS = 128;

    private Payloads() {
    }

    /** A message of the admin screen, in either direction; registered with {@link Network}. */
    public interface Payload {
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Client to server
    // ---------------------------------------------------------------------------------------------------------------

    /** The admin pressed the key: open the screen if allowed. */
    public record OpenRequest() implements Payload {
        public static final PayloadCodec<OpenRequest> STREAM_CODEC = PayloadCodec.unit(new OpenRequest());
    }

    /** Players whose name contains the filter (empty for all). */
    public record PlayersRequest(int id, String filter) implements Payload {
        public static final PayloadCodec<PlayersRequest> STREAM_CODEC = PayloadCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    NetCodecs.writeString(buf, p.filter);
                },
                buf -> new PlayersRequest(buf.readVarInt(), NetCodecs.readString(buf)));
    }

    /** Entries of a player between two times. */
    public record TimelineRequest(int id, UUID player, long from, long to) implements Payload {
        public static final PayloadCodec<TimelineRequest> STREAM_CODEC = PayloadCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeUUID(p.player);
                    buf.writeLong(p.from);
                    buf.writeLong(p.to);
                },
                buf -> new TimelineRequest(buf.readVarInt(), buf.readUUID(), buf.readLong(), buf.readLong()));
    }

    /** The state at the newest entry at or before the time. */
    public record StateRequest(int id, UUID player, long time) implements Payload {
        public static final PayloadCodec<StateRequest> STREAM_CODEC = PayloadCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeUUID(p.player);
                    buf.writeLong(p.time);
                },
                buf -> new StateRequest(buf.readVarInt(), buf.readUUID(), buf.readLong()));
    }

    public enum Action {
        RESTORE_ALL, RESTORE_INVENTORY, RESTORE_ENDER_CHEST, RESTORE_SLOTS, GIVE_COPY, LIVE_INVENTORY, LIVE_ENDER_CHEST, SNAPSHOT, UNDO,
        TELEPORT
    }

    /**
     * An admin action on the state at {@code time}.
     *
     * @param slots the selected slots for {@link Action#RESTORE_SLOTS} and {@link Action#GIVE_COPY}
     */
    public record ActionRequest(int id, UUID player, long time, Action action, int[] slots) implements Payload {
        public static final PayloadCodec<ActionRequest> STREAM_CODEC = PayloadCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeUUID(p.player);
                    buf.writeLong(p.time);
                    buf.writeEnum(p.action);
                    buf.writeVarIntArray(p.slots);
                },
                buf -> new ActionRequest(buf.readVarInt(), buf.readUUID(), buf.readLong(), buf.readEnum(Action.class),
                        buf.readVarIntArray(MAX_SLOTS)));
    }

    /**
     * An item search, see {@link com.pockyl.inventorybackups.index.SearchQuery}.
     *
     * @param player only this player, or null
     * @param since  look-back in milliseconds, 0 for all time
     * @param at     only holders at that time, 0 for any
     */
    public record SearchRequest(int id, String query, @Nullable UUID player, long since, long at) implements Payload {
        public static final PayloadCodec<SearchRequest> STREAM_CODEC = PayloadCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    NetCodecs.writeString(buf, p.query);
                    NetCodecs.writeOptionalUuid(buf, p.player);
                    buf.writeLong(p.since);
                    buf.writeLong(p.at);
                },
                buf -> new SearchRequest(buf.readVarInt(), NetCodecs.readString(buf), NetCodecs.readOptionalUuid(buf), buf.readLong(),
                        buf.readLong()));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Server to client
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Opens the screen. Sent in reply to {@link OpenRequest} and by {@code /inventory ui}.
     *
     * @param permissions bit set of {@link com.pockyl.inventorybackups.permission.Permissions#ALL} in order
     */
    public record Hello(int permissions, long serverTime, int maxResults, boolean enderChest) implements Payload {
        public static final PayloadCodec<Hello> STREAM_CODEC = PayloadCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.permissions);
                    buf.writeLong(p.serverTime);
                    buf.writeVarInt(p.maxResults);
                    buf.writeBoolean(p.enderChest);
                },
                buf -> new Hello(buf.readVarInt(), buf.readLong(), buf.readVarInt(), buf.readBoolean()));

        public boolean can(int permission) {
            return (permissions & (1 << permission)) != 0;
        }
    }

    /** A message for the admin: a denied request, a busy server or the result of an action. */
    public record Notice(int id, boolean ok, Component message) implements Payload {
        public static final PayloadCodec<Notice> STREAM_CODEC = PayloadCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeBoolean(p.ok);
                    buf.writeComponent(p.message);
                },
                buf -> new Notice(buf.readVarInt(), buf.readBoolean(), buf.readComponent()));
    }

    public record PlayersResponse(int id, List<PlayerRow> players) implements Payload {
        public static final PayloadCodec<PlayersResponse> STREAM_CODEC = PayloadCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    NetCodecs.writeList(buf, p.players, PlayerRow::write);
                },
                buf -> new PlayersResponse(buf.readVarInt(), NetCodecs.readList(buf, PlayerRow::read)));
    }

    /**
     * @param first     time of the oldest stored entry of the player, 0 when none
     * @param last      time of the newest stored entry
     * @param truncated whether plain changes were left out to stay within the limit (markers never are)
     */
    public record TimelineResponse(int id, UUID player, long from, long to, List<EntryRow> entries, long first, long last,
                                   boolean truncated) implements Payload {
        public static final PayloadCodec<TimelineResponse> STREAM_CODEC = PayloadCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeUUID(p.player);
                    buf.writeLong(p.from);
                    buf.writeLong(p.to);
                    NetCodecs.writeList(buf, p.entries, EntryRow::write);
                    buf.writeLong(p.first);
                    buf.writeLong(p.last);
                    buf.writeBoolean(p.truncated);
                },
                buf -> new TimelineResponse(buf.readVarInt(), buf.readUUID(), buf.readLong(), buf.readLong(),
                        NetCodecs.readList(buf, EntryRow::read), buf.readLong(), buf.readLong(), buf.readBoolean()));
    }

    /**
     * @param previous entry before {@code entry}, null for the oldest one
     * @param since    per slot of {@code items}: when that stack got into the slot, 0 when before the stored history
     * @param slotKeys  the keys of the mod slots in {@code items}, {@code previousItems} and {@code liveSlots}, such as
     *                  {@code curios:ring/0}
     * @param liveSlots the mod slots the player has right now, empty ones included; empty while the player is offline
     */
    public record StateResponse(int id, UUID player, EntryRow entry, Int2ObjectMap<ItemStack> items, @Nullable EntryRow previous,
                                Int2ObjectMap<ItemStack> previousItems, Int2LongMap since, Int2ObjectMap<String> slotKeys,
                                int[] liveSlots) implements Payload {
        /** The same state with the player's current mod slots added. */
        public StateResponse withLiveSlots(Int2ObjectMap<String> keys, int[] slots) {
            Int2ObjectMap<String> merged = new Int2ObjectOpenHashMap<>(slotKeys);
            merged.putAll(keys);
            return new StateResponse(id, player, entry, items, previous, previousItems, since, merged, slots);
        }

        public static final PayloadCodec<StateResponse> STREAM_CODEC = PayloadCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeUUID(p.player);
                    EntryRow.write(buf, p.entry);
                    NetCodecs.writeItems(buf, p.items);
                    buf.writeBoolean(p.previous != null);
                    if (p.previous != null) {
                        EntryRow.write(buf, p.previous);
                    }
                    NetCodecs.writeItems(buf, p.previousItems);
                    buf.writeVarInt(p.since.size());
                    p.since.int2LongEntrySet().forEach(e -> {
                        buf.writeVarInt(e.getIntKey());
                        buf.writeLong(e.getLongValue());
                    });
                    buf.writeVarInt(p.slotKeys.size());
                    p.slotKeys.int2ObjectEntrySet().forEach(e -> {
                        buf.writeVarInt(e.getIntKey());
                        NetCodecs.writeString(buf, e.getValue());
                    });
                    buf.writeVarIntArray(p.liveSlots);
                },
                buf -> {
                    int id = buf.readVarInt();
                    UUID player = buf.readUUID();
                    EntryRow entry = EntryRow.read(buf);
                    Int2ObjectMap<ItemStack> items = NetCodecs.readItems(buf);
                    EntryRow previous = buf.readBoolean() ? EntryRow.read(buf) : null;
                    Int2ObjectMap<ItemStack> previousItems = NetCodecs.readItems(buf);
                    int size = buf.readVarInt();
                    if (size < 0 || size > NetCodecs.MAX_ITEMS) {
                        throw new IllegalArgumentException("Too many slots: " + size);
                    }
                    Int2LongMap since = new Int2LongOpenHashMap(size);
                    for (int i = 0; i < size; i++) {
                        since.put(buf.readVarInt(), buf.readLong());
                    }
                    int keys = buf.readVarInt();
                    if (keys < 0 || keys > NetCodecs.MAX_ITEMS * 2) {
                        throw new IllegalArgumentException("Too many slot keys: " + keys);
                    }
                    Int2ObjectMap<String> slotKeys = new Int2ObjectOpenHashMap<>(keys);
                    for (int i = 0; i < keys; i++) {
                        slotKeys.put(buf.readVarInt(), NetCodecs.readString(buf));
                    }
                    int[] liveSlots = buf.readVarIntArray(NetCodecs.MAX_ITEMS * 2);
                    return new StateResponse(id, player, entry, items, previous, previousItems, since, slotKeys, liveSlots);
                });
    }

    public record SearchResponse(int id, String query, List<SearchRows.ItemRow> items, List<SearchRows.HolderRow> holders,
                                 List<SearchRows.LinkRow> trail, boolean truncated) implements Payload {
        public static final PayloadCodec<SearchResponse> STREAM_CODEC = PayloadCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    NetCodecs.writeString(buf, p.query);
                    NetCodecs.writeList(buf, p.items, SearchRows.ItemRow::write);
                    NetCodecs.writeList(buf, p.holders, SearchRows.HolderRow::write);
                    NetCodecs.writeList(buf, p.trail, SearchRows.LinkRow::write);
                    buf.writeBoolean(p.truncated);
                },
                buf -> new SearchResponse(buf.readVarInt(), NetCodecs.readString(buf), NetCodecs.readList(buf, SearchRows.ItemRow::read),
                        NetCodecs.readList(buf, SearchRows.HolderRow::read), NetCodecs.readList(buf, SearchRows.LinkRow::read),
                        buf.readBoolean()));
    }
}
