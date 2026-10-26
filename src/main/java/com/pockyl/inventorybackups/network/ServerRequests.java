package com.pockyl.inventorybackups.network;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.BackupService;
import com.pockyl.inventorybackups.backup.ChangeTracker;
import com.pockyl.inventorybackups.backup.OfflinePlayers;
import com.pockyl.inventorybackups.backup.Restorer;
import com.pockyl.inventorybackups.backup.Slots;
import com.pockyl.inventorybackups.command.EntrySelector;
import com.pockyl.inventorybackups.compat.ModSlots;
import com.pockyl.inventorybackups.index.ItemIndex;
import com.pockyl.inventorybackups.index.SearchQuery;
import com.pockyl.inventorybackups.index.SearchResult;
import com.pockyl.inventorybackups.index.SearchService;
import com.pockyl.inventorybackups.menu.LivePlayerMenu;
import com.pockyl.inventorybackups.permission.Permissions;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.Entry;
import com.pockyl.inventorybackups.storage.EntryContext;
import com.pockyl.inventorybackups.storage.ItemCodec;
import com.pockyl.inventorybackups.storage.PlayerInfo;
import com.pockyl.inventorybackups.storage.StateView;
import com.pockyl.inventorybackups.storage.Trigger;
import com.pockyl.inventorybackups.ui.SearchChat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.pockyl.inventorybackups.ui.Texts.tr;

/**
 * Answers the requests of the admin screen. Every request is checked against the sender's permissions and validated;
 * reads run on the storage thread and the answer is sent from the server thread. A sender may only have a few requests
 * in flight, so a modified client cannot queue up unbounded work.
 */
public final class ServerRequests {
    private static final int MAX_IN_FLIGHT = 6;
    private static final long MAX_TIMELINE_SPAN = 400L * 24 * 3_600_000L;
    private static final int SINCE_LOOK_BACK_DAYS = 3;
    /** Bytes of item data per state response; vanilla limits a payload to the client to 1 MiB. */
    private static final int ITEM_BUDGET = 600 * 1024;
    private static final Map<UUID, Integer> IN_FLIGHT = new HashMap<>();
    private static final Sender NETWORK = (player, payload) -> {
        if (Network.present(player.connection.connection)) {
            Network.sendToPlayer(player, payload);
        }
    };
    private static Sender sender = NETWORK;

    /** Where answers go; replaced by game tests, whose mock players have no network channel. */
    public interface Sender {
        void send(ServerPlayer player, Payloads.Payload payload);
    }

    public static void setSender(@Nullable Sender testSender) {
        sender = testSender == null ? NETWORK : testSender;
    }

    private ServerRequests() {
    }

    /** Whether the player's client has the screen. */
    public static boolean hasScreen(ServerPlayer player) {
        return Network.present(player.connection.connection);
    }

    /** Opens the screen on the player's client. */
    public static void sendHello(ServerPlayer player) {
        int permissions = 0;
        for (int i = 0; i < Permissions.ALL.size(); i++) {
            if (Permissions.has(player, Permissions.ALL.get(i))) {
                permissions |= 1 << i;
            }
        }
        send(player, new Payloads.Hello(permissions, System.currentTimeMillis(), Config.MAX_RESULTS.get(),
                Config.ENDER_CHEST_ENABLED.get()));
    }

    public static void open(Payloads.OpenRequest request, ServerPlayer player) {
        if (!Permissions.has(player, Permissions.UI)) {
            notice(player, -1, false, tr("inventorybackups.ui.denied"));
            return;
        }
        sendHello(player);
    }

    public static void players(Payloads.PlayersRequest request, ServerPlayer admin) {
        String filter = request.filter().toLowerCase(Locale.ROOT).trim();
        int max = Config.MAX_RESULTS.get();
        query(admin, request.id(), Permissions.VIEW, BackupStore::playerInfos, infos -> {
            MinecraftServer server = admin.server;
            Map<UUID, PlayerRow> rows = new HashMap<>();
            for (PlayerInfo info : infos) {
                String name = SearchChat.name(server, info.uuid(), info.name());
                rows.put(info.uuid(), new PlayerRow(info.uuid(), name, false, info.lastSeen(), info.lastDeath(), info.lastEntry()));
            }
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                PlayerRow known = rows.get(online.getUUID());
                rows.put(online.getUUID(), new PlayerRow(online.getUUID(), online.getScoreboardName(), true, System.currentTimeMillis(),
                        known == null ? 0 : known.lastDeath(), known == null ? 0 : known.lastEntry()));
            }
            List<PlayerRow> list = new ArrayList<>(rows.values());
            list.removeIf(row -> !filter.isEmpty() && !row.name().toLowerCase(Locale.ROOT).contains(filter));
            list.sort(Comparator.comparing((PlayerRow row) -> !row.online())
                    .thenComparing(Comparator.comparingLong(PlayerRow::lastSeen).reversed()));
            send(admin, new Payloads.PlayersResponse(request.id(), list.size() > max ? List.copyOf(list.subList(0, max)) : list));
        });
    }

    public static void timeline(Payloads.TimelineRequest request, ServerPlayer admin) {
        long from = Math.max(0, request.from());
        long to = request.to();
        if (to < from || to - from > MAX_TIMELINE_SPAN) {
            notice(admin, request.id(), false, tr("inventorybackups.ui.bad_request"));
            return;
        }
        int max = Config.MAX_RESULTS.get();
        query(admin, request.id(), Permissions.VIEW, store -> {
            List<Entry> entries = store.entries(request.player(), from, to);
            List<Entry> all = store.days(request.player()).isEmpty() ? List.of() : bounds(store, request.player());
            List<EntryRow> rows = new ArrayList<>();
            for (Entry entry : downsample(entries, max)) {
                rows.add(EntryRow.of(entry));
            }
            long first = all.isEmpty() ? 0 : all.get(0).time();
            long last = all.isEmpty() ? 0 : all.get(all.size() - 1).time();
            return new Payloads.TimelineResponse(request.id(), request.player(), from, to, rows, first, last, rows.size() < entries.size());
        }, response -> send(admin, response));
    }

    public static void state(Payloads.StateRequest request, ServerPlayer admin) {
        RegistryAccess registries = admin.server.registryAccess();
        query(admin, request.id(), Permissions.VIEW, store -> {
            Optional<StateView> found = store.stateAt(request.player(), request.time());
            if (found.isEmpty()) {
                return null;
            }
            StateView state = found.get();
            Int2LongMap since = store.since(request.player(), state.entry().time(), SINCE_LOOK_BACK_DAYS);
            Int2ObjectMap<ItemStack> items = fit(state.items(), registries, ITEM_BUDGET / 2);
            Int2ObjectMap<ItemStack> previous = fit(state.previousItems(), registries, ITEM_BUDGET / 2);
            Int2ObjectMap<String> slotKeys = new Int2ObjectOpenHashMap<>();
            for (Int2ObjectMap<ItemStack> part : List.of(items, previous)) {
                part.keySet().forEach((int slot) -> {
                    String key = Slots.isModSlot(slot) ? store.slotKeys().key(slot) : null;
                    if (key != null) {
                        slotKeys.put(slot, key);
                    }
                });
            }
            return new Payloads.StateResponse(request.id(), request.player(), EntryRow.of(state.entry()), items,
                    state.previous() == null ? null : EntryRow.of(state.previous()), previous, since, slotKeys, new int[0]);
        }, response -> {
            if (response == null) {
                notice(admin, request.id(), false, tr("inventorybackups.error.backup_not_found", EntrySelector.display(request.time())));
            } else {
                send(admin, withLiveSlots(admin, response));
            }
        });
    }

    /** Adds the mod slots the player has now (read on the main thread), so the screen can show empty ones too. */
    private static Payloads.StateResponse withLiveSlots(ServerPlayer admin, Payloads.StateResponse response) {
        ServerPlayer player = admin.server.getPlayerList().getPlayer(response.player());
        BackupStore store = BackupService.store();
        if (player == null || store == null || !ModSlots.any()) {
            return response;
        }
        Int2ObjectMap<String> keys = new Int2ObjectOpenHashMap<>();
        IntArrayList slots = new IntArrayList();
        for (String key : ModSlots.read(player).keySet()) {
            int number = store.slotKeys().number(key);
            if (number >= 0) {
                keys.put(number, key);
                slots.add(number);
            }
        }
        return response.withLiveSlots(keys, slots.toIntArray());
    }

    public static void action(Payloads.ActionRequest request, ServerPlayer admin) {
        PermissionNode<Boolean> node = switch (request.action()) {
            case RESTORE_ALL, RESTORE_INVENTORY, RESTORE_ENDER_CHEST, RESTORE_SLOTS, GIVE_COPY, UNDO -> Permissions.RESTORE;
            case LIVE_INVENTORY, LIVE_ENDER_CHEST, TELEPORT -> Permissions.EDIT;
            case SNAPSHOT -> Permissions.VIEW;
        };
        IntArrayList slots = new IntArrayList();
        for (int slot : request.slots()) {
            if (Slots.section(slot) != Slots.Section.UNKNOWN && !slots.contains(slot)) {
                slots.add(slot);
            }
        }
        UUID target = request.player();
        switch (request.action()) {
            case LIVE_INVENTORY, LIVE_ENDER_CHEST -> {
                if (!check(admin, request.id(), node)) {
                    return;
                }
                ServerPlayer player = editable(admin, request.id(), target);
                if (player != null) {
                    // The menu owns an offline copy from here on and releases it when it closes.
                    boolean ender = request.action() == Payloads.Action.LIVE_ENDER_CHEST;
                    LivePlayerMenu.open(admin, player, ender, tr(ender ? "inventorybackups.enderchest.player.title"
                            : "inventorybackups.inventory.player.title", player.getScoreboardName()));
                }
            }
            case SNAPSHOT -> {
                if (!check(admin, request.id(), node)) {
                    return;
                }
                ServerPlayer player = online(admin, request.id(), target);
                if (player != null) {
                    ChangeTracker.capture(player, Trigger.COMMAND, EntryContext.of(player).withActor(admin.getScoreboardName()));
                    notice(admin, request.id(), true, tr("inventorybackups.success.snapshot", player.getScoreboardName()));
                }
            }
            case UNDO -> query(admin, request.id(), node, store -> {
                List<Entry> entries = store.entries(target, 0, Long.MAX_VALUE);
                for (int i = entries.size() - 1; i >= 0; i--) {
                    if (entries.get(i).trigger() == Trigger.RESTORE) {
                        return store.stateAt(target, entries.get(i).time() - 1).orElse(null);
                    }
                }
                return null;
            }, state -> {
                ServerPlayer player = state == null ? null : editable(admin, request.id(), target);
                if (state == null) {
                    notice(admin, request.id(), false, tr("inventorybackups.error.no_undo", target.toString()));
                } else if (player != null) {
                    Restorer.restore(player, state, true, Config.ENDER_CHEST_ENABLED.get(), admin.getScoreboardName());
                    OfflinePlayers.release(player);
                    notice(admin, request.id(), true, tr("inventorybackups.success.undone", player.getScoreboardName(),
                            EntrySelector.display(state.entry().time())));
                }
            });
            default -> query(admin, request.id(), node, store -> store.stateAt(target, request.time()).orElse(null),
                    state -> act(admin, request, state, slots));
        }
    }

    private static void act(ServerPlayer admin, Payloads.ActionRequest request, @Nullable StateView state, IntArrayList slots) {
        if (state == null) {
            notice(admin, request.id(), false, tr("inventorybackups.error.backup_not_found", EntrySelector.display(request.time())));
            return;
        }
        String time = EntrySelector.display(state.entry().time());
        switch (request.action()) {
            case GIVE_COPY -> {
                List<ItemStack> stacks = new ArrayList<>();
                for (int slot : slots) {
                    ItemStack stack = state.items().get(slot);
                    if (stack != null) {
                        stacks.add(stack);
                    }
                }
                if (stacks.isEmpty()) {
                    notice(admin, request.id(), false, tr("inventorybackups.ui.nothing_selected"));
                    return;
                }
                Restorer.give(admin, stacks);
                InventoryBackups.LOGGER.info("{} copied {} stacks from the state of {} at {}", admin.getScoreboardName(), stacks.size(),
                        request.player(), time);
                notice(admin, request.id(), true, tr("inventorybackups.ui.copied", stacks.size()));
            }
            case TELEPORT -> {
                EntryContext context = state.entry().context();
                ResourceLocation dimension = ResourceLocation.tryParse(context.dimension());
                ServerLevel level = dimension == null ? null : admin.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
                if (level == null) {
                    notice(admin, request.id(), false, tr("inventorybackups.ui.no_position"));
                    return;
                }
                BlockPos pos = context.pos();
                admin.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, admin.getYRot(), admin.getXRot());
                notice(admin, request.id(), true, tr("inventorybackups.ui.teleported", ChatPosition.of(context)));
            }
            default -> {
                if (request.action() == Payloads.Action.RESTORE_SLOTS && slots.isEmpty()) {
                    notice(admin, request.id(), false, tr("inventorybackups.ui.nothing_selected"));
                    return;
                }
                ServerPlayer player = editable(admin, request.id(), request.player());
                if (player == null) {
                    return;
                }
                if (request.action() == Payloads.Action.RESTORE_SLOTS) {
                    Restorer.restoreSlots(player, state, slots, admin.getScoreboardName());
                    notice(admin, request.id(), true, tr("inventorybackups.ui.restored_slots", slots.size(), player.getScoreboardName(),
                            time));
                } else {
                    boolean inventory = request.action() != Payloads.Action.RESTORE_ENDER_CHEST;
                    boolean ender = request.action() != Payloads.Action.RESTORE_INVENTORY && Config.ENDER_CHEST_ENABLED.get();
                    Restorer.restore(player, state, inventory, ender, admin.getScoreboardName());
                    notice(admin, request.id(), true, tr("inventorybackups.ui.restored", player.getScoreboardName(), time));
                }
                player.sendSystemMessage(tr("inventorybackups.inventory.info.restored", time));
                OfflinePlayers.release(player);
            }
        }
    }

    public static void search(Payloads.SearchRequest request, ServerPlayer admin) {
        SearchQuery query = SearchQuery.parse(request.query());
        if (query == null) {
            notice(admin, request.id(), false, tr("inventorybackups.error.bad_query"));
            return;
        }
        ItemIndex index = BackupService.index();
        RegistryAccess registries = admin.server.registryAccess();
        long now = System.currentTimeMillis();
        long since = request.since() > 0 ? now - request.since() : 0;
        SearchService.Options options = new SearchService.Options(request.player(), since, since, Math.max(0, request.at()),
                Config.MAX_RESULTS.get(), Config.PROBABLE_LINK_SECONDS.get() * 1000L, Config.PROBABLE_LINK_DISTANCE.get());
        query(admin, request.id(), Permissions.SEARCH, store -> {
            if (index == null) {
                return null;
            }
            SearchResult result = SearchService.search(store, index, query, options);
            List<SearchRows.ItemRow> items = new ArrayList<>();
            for (SearchResult.MatchedItem item : result.items()) {
                ItemStack sample = item.sample() == null ? ItemStack.EMPTY : ItemCodec.decode(registries, item.sample(), 1);
                items.add(new SearchRows.ItemRow(item.itemId(), item.fingerprint() == null ? "" : item.fingerprint(),
                        item.name() == null ? "" : item.name(), trimmed(sample, registries)));
            }
            List<SearchRows.HolderRow> holders = result.holders().stream().map(SearchRows.HolderRow::of).toList();
            List<SearchRows.LinkRow> trail = result.trail().stream().map(SearchRows.LinkRow::of).toList();
            return new Payloads.SearchResponse(request.id(), query.text(), items, holders, trail, result.truncated());
        }, response -> {
            if (response == null) {
                notice(admin, request.id(), false, tr("inventorybackups.error.storage_unavailable"));
            } else {
                send(admin, response);
            }
        });
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** Runs a read on the storage thread after the permission and flight checks, then continues on the server thread. */
    private static <T> void query(ServerPlayer admin, int id, PermissionNode<Boolean> node, Function<BackupStore, T> read,
                                  Consumer<T> then) {
        if (!check(admin, id, node)) {
            return;
        }
        BackupStore store = BackupService.store();
        if (store == null) {
            notice(admin, id, false, tr("inventorybackups.error.storage_unavailable"));
            return;
        }
        UUID uuid = admin.getUUID();
        int flying = IN_FLIGHT.getOrDefault(uuid, 0);
        if (flying >= MAX_IN_FLIGHT) {
            notice(admin, id, false, tr("inventorybackups.ui.busy"));
            return;
        }
        IN_FLIGHT.put(uuid, flying + 1);
        Supplier<T> task = () -> read.apply(store);
        store.call(task).whenCompleteAsync((result, error) -> {
            IN_FLIGHT.computeIfPresent(uuid, (key, count) -> count <= 1 ? null : count - 1);
            if (error != null) {
                InventoryBackups.LOGGER.error("Admin screen request failed", error);
                notice(admin, id, false, tr("inventorybackups.error.storage_unavailable"));
            } else if (!admin.hasDisconnected()) {
                then.accept(result);
            }
        }, admin.server);
    }

    private static boolean check(ServerPlayer admin, int id, PermissionNode<Boolean> node) {
        if (Permissions.has(admin, node)) {
            return true;
        }
        notice(admin, id, false, tr("inventorybackups.ui.no_permission", node.getNodeName()));
        return false;
    }

    /**
     * The player to change: the online one, or a copy loaded from the save file while they are offline. Release it with
     * {@link OfflinePlayers#release} after use (nothing to release for online players).
     */
    @Nullable
    private static ServerPlayer editable(ServerPlayer admin, int id, UUID target) {
        ServerPlayer online = admin.server.getPlayerList().getPlayer(target);
        if (online != null) {
            return online;
        }
        GameProfileCache cache = admin.server.getProfileCache();
        String name = cache == null ? target.toString() : cache.get(target).map(GameProfile::getName).orElse(target.toString());
        Optional<ServerPlayer> offline = OfflinePlayers.acquire(admin.server, target, name);
        if (offline.isEmpty()) {
            notice(admin, id, false, tr("inventorybackups.error.no_save_file", name));
        }
        return offline.orElse(null);
    }

    @Nullable
    private static ServerPlayer online(ServerPlayer admin, int id, UUID target) {
        ServerPlayer player = admin.server.getPlayerList().getPlayer(target);
        if (player == null) {
            notice(admin, id, false, tr("inventorybackups.ui.offline"));
        }
        return player;
    }

    private static void notice(ServerPlayer player, int id, boolean ok, Component message) {
        send(player, new Payloads.Notice(id, ok, message));
    }

    private static void send(ServerPlayer player, Payloads.Payload payload) {
        sender.send(player, payload);
    }

    /** Oldest and newest entries of a player, for the "All" zoom. */
    private static List<Entry> bounds(BackupStore store, UUID player) {
        List<Entry> bounds = new ArrayList<>();
        List<LocalDate> days = store.days(player);
        store.day(player, days.get(0)).ifPresent(log -> {
            if (!log.entries().isEmpty()) {
                bounds.add(log.entries().get(0));
            }
        });
        store.day(player, days.get(days.size() - 1)).ifPresent(log -> {
            if (!log.entries().isEmpty()) {
                bounds.add(log.entries().get(log.entries().size() - 1));
            }
        });
        return bounds;
    }

    /** Keeps every marker and as many evenly spread changes as fit, plus the newest entry. */
    static List<Entry> downsample(List<Entry> entries, int max) {
        if (entries.size() <= max) {
            return entries;
        }
        long markers = entries.stream().filter(entry -> entry.trigger() != Trigger.CHANGE).count();
        long changes = entries.size() - markers;
        long room = Math.max(1, max - markers);
        long step = (changes + room - 1) / room;
        List<Entry> kept = new ArrayList<>();
        long seen = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            boolean change = entry.trigger() == Trigger.CHANGE;
            if (!change || seen++ % step == 0 || i == entries.size() - 1) {
                kept.add(entry);
            }
        }
        return kept;
    }

    /** Stacks within a byte budget; the largest ones beyond it are replaced by a plain stack of the same item. */
    static Int2ObjectMap<ItemStack> fit(Int2ObjectMap<ItemStack> items, RegistryAccess registries, int budget) {
        Int2ObjectMap<ItemStack> result = new Int2ObjectOpenHashMap<>();
        Map<Integer, Integer> sizes = new HashMap<>();
        int total = 0;
        for (Int2ObjectMap.Entry<ItemStack> entry : items.int2ObjectEntrySet()) {
            int size = size(entry.getValue(), registries);
            sizes.put(entry.getIntKey(), size);
            total += size;
            result.put(entry.getIntKey(), entry.getValue());
        }
        if (total <= budget) {
            return result;
        }
        List<Integer> largest = new ArrayList<>(sizes.keySet());
        largest.sort(Comparator.comparing(sizes::get).reversed());
        for (int slot : largest) {
            if (total <= budget) {
                break;
            }
            ItemStack plain = trimmed(result.get(slot), registries);
            total -= sizes.get(slot) - size(plain, registries);
            result.put(slot, plain);
        }
        return result;
    }

    /** A stack without its data, marked as too large to show, when it is too large to send. */
    private static ItemStack trimmed(ItemStack stack, RegistryAccess registries) {
        if (stack.isEmpty() || size(stack, registries) <= ITEM_BUDGET / 8) {
            return stack;
        }
        ItemStack plain = new ItemStack(stack.getItem(), stack.getCount());
        plain.setHoverName(tr("inventorybackups.ui.too_large", stack.getHoverName()));
        return plain;
    }

    private static int size(ItemStack stack, RegistryAccess registries) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeItem(stack);
            return buf.readableBytes();
        } finally {
            buf.release();
        }
    }

    /** A readable dimension and position. */
    private static final class ChatPosition {
        private ChatPosition() {
        }

        static String of(EntryContext context) {
            ResourceLocation id = ResourceLocation.tryParse(context.dimension());
            boolean vanilla = id != null && id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE);
            String dimension = vanilla ? id.getPath() : context.dimension();
            return dimension + " " + context.pos().getX() + " " + context.pos().getY() + " " + context.pos().getZ();
        }
    }
}
