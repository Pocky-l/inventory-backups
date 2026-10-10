package com.pockyl.inventorybackups.index;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.EntryContext;
import com.pockyl.inventorybackups.storage.Flow;
import com.pockyl.inventorybackups.storage.FlowType;
import com.pockyl.inventorybackups.storage.PlayerInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Answers item searches from the index and the flow log. Runs on the storage thread.
 * <p>
 * The transfer trail has two kinds of links. Exact ones come from recorded events: a pickup of items another player
 * dropped (or lost on death), and a container session that took items another player had put into the same container.
 * Probable ones come from timing: the item left one player completely and appeared at another player within a few
 * seconds and blocks, with no exact link explaining it (handing items over by other means, mods, lag).
 */
public final class SearchService {
    private SearchService() {
    }

    /**
     * @param player       only this player, or null for everyone
     * @param holdersSince only holders seen at or after this time (current holders always)
     * @param trailSince   only transfers made at or after this time
     * @param at           only holders who held the item at this time, 0 for any time
     */
    public record Options(@Nullable UUID player, long holdersSince, long trailSince, long at, int maxResults, long linkWindow,
                          int linkDistance) {
    }

    public static SearchResult search(BackupStore store, ItemIndex index, SearchQuery query, Options options) {
        List<ItemIndex.KeyEntry> keys = match(index, query);
        boolean truncated = false;
        List<SearchResult.MatchedItem> items = new ArrayList<>();
        for (ItemIndex.KeyEntry key : keys) {
            if (items.size() >= options.maxResults()) {
                truncated = true;
                break;
            }
            items.add(new SearchResult.MatchedItem(key.key(), key.itemId(), key.name(), key.sample()));
        }
        // Item searches also list the distinctive stacks of that item, so one specific stack can be traced from there.
        if (query.kind() == SearchQuery.Kind.ITEM) {
            for (ItemIndex.KeyEntry key : index.keys()) {
                if (key.isFingerprint() && key.itemId().equals(query.value()) && items.size() < options.maxResults()) {
                    items.add(new SearchResult.MatchedItem(key.key(), key.itemId(), key.name(), key.sample()));
                }
            }
        }

        List<SearchResult.Holder> holders = holders(store, keys, options);
        if (holders.size() > options.maxResults()) {
            holders = holders.subList(0, options.maxResults());
            truncated = true;
        }
        List<SearchResult.TrailLink> trail = trail(store, index, query, keys, options);
        if (trail.size() > options.maxResults()) {
            trail = trail.subList(0, options.maxResults());
            truncated = true;
        }
        return new SearchResult(query, items, List.copyOf(holders), List.copyOf(trail), truncated);
    }

    /** Puts and takes at one container, newest first. */
    public static List<Flow> containerHistory(BackupStore store, String dimension, BlockPos pos, long since, int max) {
        List<Flow> flows = new ArrayList<>();
        for (Flow flow : store.flows(since, Long.MAX_VALUE)) {
            if ((flow.type() == FlowType.PUT || flow.type() == FlowType.TAKE) && pos.equals(flow.containerPos())
                    && flow.dimension().equals(dimension)) {
                flows.add(flow);
            }
        }
        flows.sort(Comparator.comparingLong(Flow::time).reversed());
        return flows.size() > max ? List.copyOf(flows.subList(0, max)) : flows;
    }

    static List<ItemIndex.KeyEntry> match(ItemIndex index, SearchQuery query) {
        List<ItemIndex.KeyEntry> matched = new ArrayList<>();
        switch (query.kind()) {
            case ITEM -> {
                ItemIndex.KeyEntry entry = index.get(ItemKeys.ITEM_PREFIX + query.value());
                if (entry != null) {
                    matched.add(entry);
                }
            }
            case FINGERPRINT -> {
                ItemIndex.KeyEntry entry = index.get(ItemKeys.FINGERPRINT_PREFIX + query.value());
                if (entry != null) {
                    matched.add(entry);
                }
            }
            case NAME -> {
                for (ItemIndex.KeyEntry entry : index.keys()) {
                    if (entry.isFingerprint() && entry.name() != null && entry.name().contains(query.value())) {
                        matched.add(entry);
                    }
                }
            }
            case ENCHANTMENT -> {
                for (ItemIndex.KeyEntry entry : index.keys()) {
                    if (entry.isFingerprint() && entry.enchantments().contains(query.value())) {
                        matched.add(entry);
                    }
                }
            }
        }
        return matched;
    }

    private static List<SearchResult.Holder> holders(BackupStore store, List<ItemIndex.KeyEntry> keys, Options options) {
        Map<UUID, Merged> merged = new HashMap<>();
        for (ItemIndex.KeyEntry key : keys) {
            key.holders().forEach((uuid, presence) -> {
                if (options.player() != null && !options.player().equals(uuid)) {
                    return;
                }
                merged.computeIfAbsent(uuid, u -> new Merged()).add(presence);
            });
        }
        List<SearchResult.Holder> holders = new ArrayList<>();
        merged.forEach((uuid, presence) -> {
            if (!presence.holdsNow() && presence.last < options.holdersSince()) {
                return;
            }
            if (options.at() > 0 && !presence.heldAt(options.at())) {
                return;
            }
            holders.add(new SearchResult.Holder(uuid, name(store, uuid), presence.count, presence.max, presence.first, presence.last,
                    presence.ranges.toLongArray()));
        });
        holders.sort(Comparator.comparing((SearchResult.Holder h) -> !h.holdsNow())
                .thenComparing(Comparator.comparingInt(SearchResult.Holder::count).reversed())
                .thenComparing(Comparator.comparingLong(SearchResult.Holder::last).reversed()));
        return holders;
    }

    /** Presences of several keys of one player, added up. */
    private static final class Merged {
        private int count;
        private int max;
        private long first = Long.MAX_VALUE;
        private long last;
        private final LongArrayList ranges = new LongArrayList();

        void add(Presence presence) {
            count += presence.count();
            max = Math.max(max, presence.max());
            first = Math.min(first, presence.first());
            last = Math.max(last, presence.last());
            ranges.addElements(ranges.size(), presence.ranges());
        }

        boolean holdsNow() {
            return count > 0;
        }

        boolean heldAt(long time) {
            for (int i = 0; i < ranges.size(); i += 2) {
                if (ranges.getLong(i) <= time && time <= ranges.getLong(i + 1)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static List<SearchResult.TrailLink> trail(BackupStore store, ItemIndex index, SearchQuery query,
                                                      List<ItemIndex.KeyEntry> keys, Options options) {
        Set<String> keyNames = new HashSet<>();
        keys.forEach(key -> keyNames.add(key.key()));
        Long2ObjectOpenHashMap<Boolean> matches = new Long2ObjectOpenHashMap<>();
        List<Flow> flows = new ArrayList<>();
        for (Flow flow : store.flows(options.trailSince(), Long.MAX_VALUE)) {
            Boolean match = matches.get(flow.hash());
            if (match == null) {
                match = flowMatches(store, query, keyNames, flow.item());
                matches.put(flow.hash(), match);
            }
            if (match) {
                flows.add(flow);
            }
        }

        List<SearchResult.TrailLink> links = new ArrayList<>();
        exactLinks(flows, links);
        probableLinks(store, keys, options, links);
        if (options.player() != null) {
            links.removeIf(link -> !options.player().equals(link.to()) && !options.player().equals(link.from()));
        }
        links.sort(Comparator.comparingLong(SearchResult.TrailLink::time).reversed());
        return links;
    }

    private static boolean flowMatches(BackupStore store, SearchQuery query, Set<String> keyNames, CompoundTag item) {
        ItemKeys keys = ItemKeys.of(item, store.registries());
        return switch (query.kind()) {
            case ITEM -> keys.itemId().equals(query.value());
            default -> keys.fingerprintKey() != null && keyNames.contains(keys.fingerprintKey());
        };
    }

    private static void exactLinks(List<Flow> flows, List<SearchResult.TrailLink> links) {
        Map<String, Deque<int[]>> puts = new HashMap<>();
        Map<UUID, Flow> lastDrop = new HashMap<>();
        for (int i = 0; i < flows.size(); i++) {
            Flow flow = flows.get(i);
            switch (flow.type()) {
                case TOSS, DEATH_DROP -> lastDrop.put(flow.player(), flow);
                case PICKUP -> {
                    if (flow.source() != null && !flow.source().equals(flow.player())) {
                        Flow drop = lastDrop.get(flow.source());
                        SearchResult.LinkKind kind = drop != null && drop.type() == FlowType.DEATH_DROP
                                ? SearchResult.LinkKind.DEATH_LOOT : SearchResult.LinkKind.DROP;
                        links.add(link(flow, kind, flow.source(), flow.sourceName() == null ? "" : flow.sourceName(), flow.count(), 0));
                    }
                }
                case PUT -> puts.computeIfAbsent(containerKey(flow), k -> new ArrayDeque<>()).add(new int[] {i, flow.count()});
                case TAKE -> {
                    Deque<int[]> queue = puts.get(containerKey(flow));
                    int wanted = flow.count();
                    while (queue != null && wanted > 0 && !queue.isEmpty()) {
                        int[] put = queue.peekFirst();
                        Flow putFlow = flows.get(put[0]);
                        int moved = Math.min(wanted, put[1]);
                        if (!putFlow.player().equals(flow.player())) {
                            links.add(link(flow, SearchResult.LinkKind.CONTAINER, putFlow.player(), putFlow.playerName(), moved, 0));
                        }
                        put[1] -= moved;
                        wanted -= moved;
                        if (put[1] == 0) {
                            queue.pollFirst();
                        }
                    }
                }
            }
        }
    }

    private static String containerKey(Flow flow) {
        return flow.dimension() + "@" + (flow.containerPos() == null ? "" : flow.containerPos().asLong());
    }

    private static SearchResult.TrailLink link(Flow flow, SearchResult.LinkKind kind, @Nullable UUID from, String fromName, int count,
                                               long gap) {
        return new SearchResult.TrailLink(flow.time(), kind, from, fromName, flow.player(), flow.playerName(), count,
                flow.item().getString("id"), flow.dimension(), flow.pos(), flow.container(), flow.containerPos(), gap);
    }

    /** A player starting or stopping to hold an item. */
    private record Change(UUID player, long time) {
    }

    /** Range ends at one player matched to range starts at another within the time window and distance. */
    private static void probableLinks(BackupStore store, List<ItemIndex.KeyEntry> keys, Options options,
                                      List<SearchResult.TrailLink> links) {
        List<Change> ends = new ArrayList<>();
        List<Change> starts = new ArrayList<>();
        for (ItemIndex.KeyEntry key : keys) {
            key.holders().forEach((uuid, presence) -> {
                long[] ranges = presence.ranges();
                for (int i = 0; i < ranges.length; i += 2) {
                    if (ranges[i] >= options.trailSince()) {
                        starts.add(new Change(uuid, ranges[i]));
                    }
                    if (ranges[i + 1] != Long.MAX_VALUE && ranges[i + 1] >= options.trailSince()) {
                        ends.add(new Change(uuid, ranges[i + 1]));
                    }
                }
            });
        }
        String itemId = keys.isEmpty() ? "" : keys.get(0).itemId();
        starts.sort(Comparator.comparingLong(Change::time));
        int checked = 0;
        for (Change start : starts) {
            for (Change end : ends) {
                long gap = start.time() - end.time();
                if (end.player().equals(start.player()) || gap < 0 || gap > options.linkWindow()
                        || explained(links, end, start, options.linkWindow())) {
                    continue;
                }
                if (++checked > options.maxResults()) {
                    return;
                }
                Optional<EntryContext> from = contextAt(store, end.player(), end.time());
                Optional<EntryContext> to = contextAt(store, start.player(), start.time());
                if (from.isEmpty() || to.isEmpty() || !near(from.get(), to.get(), options.linkDistance())) {
                    continue;
                }
                links.add(new SearchResult.TrailLink(start.time(), SearchResult.LinkKind.PROBABLE, end.player(), name(store, end.player()),
                        start.player(), name(store, start.player()), 0, itemId, to.get().dimension(), to.get().pos(), null, null, gap));
                break;
            }
        }
    }

    /** Whether an exact link between the two players around that time already explains the change. */
    private static boolean explained(List<SearchResult.TrailLink> links, Change end, Change start, long window) {
        for (SearchResult.TrailLink link : links) {
            if (link.kind().exact() && end.player().equals(link.from()) && start.player().equals(link.to())
                    && Math.abs(link.time() - start.time()) <= window) {
                return true;
            }
        }
        return false;
    }

    private static Optional<EntryContext> contextAt(BackupStore store, UUID player, long time) {
        return store.stateAt(player, time).map(state -> state.entry().context());
    }

    private static boolean near(EntryContext a, EntryContext b, int distance) {
        return !a.dimension().isEmpty() && a.dimension().equals(b.dimension()) && a.pos().closerThan(b.pos(), distance);
    }

    private static String name(BackupStore store, UUID player) {
        PlayerInfo info = store.playerInfo(player);
        return info == null ? "" : info.name();
    }
}
