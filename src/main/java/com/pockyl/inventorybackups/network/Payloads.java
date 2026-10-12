package com.pockyl.inventorybackups.network;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.InventoryBackups;

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

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(InventoryBackups.id(path));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Client to server
    // ---------------------------------------------------------------------------------------------------------------

    /** The admin pressed the key: open the screen if allowed. */
    public record OpenRequest() implements CustomPacketPayload {
        public static final Type<OpenRequest> TYPE = Payloads.payloadType("open");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenRequest> STREAM_CODEC = StreamCodec.unit(new OpenRequest());

        @Override
        public Type<OpenRequest> type() {
            return TYPE;
        }
    }

    /** Players whose name contains the filter (empty for all). */
    public record PlayersRequest(int id, String filter) implements CustomPacketPayload {
        public static final Type<PlayersRequest> TYPE = Payloads.payloadType("players_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayersRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    NetCodecs.writeString(buf, p.filter);
                },
                buf -> new PlayersRequest(buf.readVarInt(), NetCodecs.readString(buf)));

        @Override
        public Type<PlayersRequest> type() {
            return TYPE;
        }
    }

    /** Entries of a player between two times. */
    public record TimelineRequest(int id, UUID player, long from, long to) implements CustomPacketPayload {
        public static final Type<TimelineRequest> TYPE = Payloads.payloadType("timeline_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, TimelineRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeUUID(p.player);
                    buf.writeLong(p.from);
                    buf.writeLong(p.to);
                },
                buf -> new TimelineRequest(buf.readVarInt(), buf.readUUID(), buf.readLong(), buf.readLong()));

        @Override
        public Type<TimelineRequest> type() {
            return TYPE;
        }
    }

    /** The state at the newest entry at or before the time. */
    public record StateRequest(int id, UUID player, long time) implements CustomPacketPayload {
        public static final Type<StateRequest> TYPE = Payloads.payloadType("state_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, StateRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeUUID(p.player);
                    buf.writeLong(p.time);
                },
                buf -> new StateRequest(buf.readVarInt(), buf.readUUID(), buf.readLong()));

        @Override
        public Type<StateRequest> type() {
            return TYPE;
        }
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
    public record ActionRequest(int id, UUID player, long time, Action action, int[] slots) implements CustomPacketPayload {
        public static final Type<ActionRequest> TYPE = Payloads.payloadType("action_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeUUID(p.player);
                    buf.writeLong(p.time);
                    buf.writeEnum(p.action);
                    buf.writeVarIntArray(p.slots);
                },
                buf -> new ActionRequest(buf.readVarInt(), buf.readUUID(), buf.readLong(), buf.readEnum(Action.class),
                        buf.readVarIntArray(MAX_SLOTS)));

        @Override
        public Type<ActionRequest> type() {
            return TYPE;
        }
    }

    /**
     * An item search, see {@link com.pockyl.inventorybackups.index.SearchQuery}.
     *
     * @param player only this player, or null
     * @param since  look-back in milliseconds, 0 for all time
     * @param at     only holders at that time, 0 for any
     */
    public record SearchRequest(int id, String query, @Nullable UUID player, long since, long at) implements CustomPacketPayload {
        public static final Type<SearchRequest> TYPE = Payloads.payloadType("search_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, SearchRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    NetCodecs.writeString(buf, p.query);
                    NetCodecs.writeOptionalUuid(buf, p.player);
                    buf.writeLong(p.since);
                    buf.writeLong(p.at);
                },
                buf -> new SearchRequest(buf.readVarInt(), NetCodecs.readString(buf), NetCodecs.readOptionalUuid(buf), buf.readLong(),
                        buf.readLong()));

        @Override
        public Type<SearchRequest> type() {
            return TYPE;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Server to client
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Opens the screen. Sent in reply to {@link OpenRequest} and by {@code /inventory ui}.
     *
     * @param permissions bit set of {@link com.pockyl.inventorybackups.permission.Permissions#ALL} in order
     */
    public record Hello(int permissions, long serverTime, int maxResults, boolean enderChest) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = Payloads.payloadType("hello");
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> STREAM_CODEC = StreamCodec.of(
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

        @Override
        public Type<Hello> type() {
            return TYPE;
        }
    }

    /** A message for the admin: a denied request, a busy server or the result of an action. */
    public record Notice(int id, boolean ok, Component message) implements CustomPacketPayload {
        public static final Type<Notice> TYPE = Payloads.payloadType("notice");
        public static final StreamCodec<RegistryFriendlyByteBuf, Notice> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeBoolean(p.ok);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.message);
                },
                buf -> new Notice(buf.readVarInt(), buf.readBoolean(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf)));

        @Override
        public Type<Notice> type() {
            return TYPE;
        }
    }

    public record PlayersResponse(int id, List<PlayerRow> players) implements CustomPacketPayload {
        public static final Type<PlayersResponse> TYPE = Payloads.payloadType("players");
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayersResponse> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    NetCodecs.writeList(buf, p.players, PlayerRow::write);
                },
                buf -> new PlayersResponse(buf.readVarInt(), NetCodecs.readList(buf, PlayerRow::read)));

        @Override
        public Type<PlayersResponse> type() {
            return TYPE;
        }
    }

    /**
     * @param first     time of the oldest stored entry of the player, 0 when none
     * @param last      time of the newest stored entry
     * @param truncated whether plain changes were left out to stay within the limit (markers never are)
     */
    public record TimelineResponse(int id, UUID player, long from, long to, List<EntryRow> entries, long first, long last,
                                   boolean truncated) implements CustomPacketPayload {
        public static final Type<TimelineResponse> TYPE = Payloads.payloadType("timeline");
        public static final StreamCodec<RegistryFriendlyByteBuf, TimelineResponse> STREAM_CODEC = StreamCodec.of(
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

        @Override
        public Type<TimelineResponse> type() {
            return TYPE;
        }
    }

    /**
     * @param previous entry before {@code entry}, null for the oldest one
     * @param since    per slot of {@code items}: when that stack got into the slot, 0 when before the stored history
     * @param slotKeys the keys of the mod slots in {@code items} and {@code previousItems}, such as {@code curios:ring/0}
     */
    public record StateResponse(int id, UUID player, EntryRow entry, Int2ObjectMap<ItemStack> items, @Nullable EntryRow previous,
                                Int2ObjectMap<ItemStack> previousItems, Int2LongMap since, Int2ObjectMap<String> slotKeys)
            implements CustomPacketPayload {
        public static final Type<StateResponse> TYPE = Payloads.payloadType("state");
        public static final StreamCodec<RegistryFriendlyByteBuf, StateResponse> STREAM_CODEC = StreamCodec.of(
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
                    return new StateResponse(id, player, entry, items, previous, previousItems, since, slotKeys);
                });

        @Override
        public Type<StateResponse> type() {
            return TYPE;
        }
    }

    public record SearchResponse(int id, String query, List<SearchRows.ItemRow> items, List<SearchRows.HolderRow> holders,
                                 List<SearchRows.LinkRow> trail, boolean truncated) implements CustomPacketPayload {
        public static final Type<SearchResponse> TYPE = Payloads.payloadType("search");
        public static final StreamCodec<RegistryFriendlyByteBuf, SearchResponse> STREAM_CODEC = StreamCodec.of(
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

        @Override
        public Type<SearchResponse> type() {
            return TYPE;
        }
    }
}
