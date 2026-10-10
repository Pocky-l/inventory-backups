package com.pockyl.inventorybackups.storage;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.Slots;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The write side of one player's timeline, owned by the storage thread: the current state and the day file entries are
 * appended to.
 */
final class PlayerLog {
    private final UUID player;
    private final Path dir;
    private boolean loaded;
    @Nullable
    private DayLog open;
    private long openLength;
    private Int2ObjectMap<StackRef> state = new Int2ObjectOpenHashMap<>();
    /** Pool items of the stacks in {@link #state}, so a new day file can start with a keyframe without reading old files. */
    private final Long2ObjectMap<CompoundTag> items = new Long2ObjectOpenHashMap<>();
    private long lastTime = Long.MIN_VALUE;
    private int sinceKeyframe;

    PlayerLog(UUID player, Path dir) {
        this.player = player;
        this.dir = dir;
    }

    /** The result of an append: the entry and the states around it, for the index. */
    record Appended(Entry entry, int bytes, Int2ObjectMap<StackRef> before, Int2ObjectMap<StackRef> after,
                    Long2ObjectMap<CompoundTag> items) {
    }

    Int2ObjectMap<StackRef> state() {
        ensureLoaded();
        return state;
    }

    long lastTime() {
        ensureLoaded();
        return lastTime;
    }

    /** The in-memory log of the day being appended to, so readers do not have to re-read that file. */
    @Nullable
    DayLog openLog(LocalDate day) {
        return open != null && open.day().equals(day) ? open : null;
    }

    /** Forgets the cached state, for example after the files changed underneath (retention, tests). */
    void invalidate() {
        loaded = false;
        open = null;
        state = new Int2ObjectOpenHashMap<>();
        items.clear();
        lastTime = Long.MIN_VALUE;
        sinceKeyframe = 0;
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        List<LocalDate> days = DayFiles.days(dir);
        for (int i = days.size() - 1; i >= 0; i--) {
            LocalDate day = days.get(i);
            try {
                LogFile.Read read = LogFile.read(DayFiles.file(dir, day), day);
                DayLog log = read.log();
                if (log.entries().isEmpty()) {
                    continue;
                }
                int last = log.entries().size() - 1;
                state = log.stateAt(last);
                for (StackRef ref : state.values()) {
                    items.put(ref.hash(), log.poolTag(log.indexOf(ref.hash())));
                }
                lastTime = log.entries().get(last).time();
                if (i == days.size() - 1) {
                    open = log;
                    openLength = read.validLength();
                    sinceKeyframe = 0;
                    for (int j = last; j >= 0 && !log.entries().get(j).keyframe(); j--) {
                        sinceKeyframe++;
                    }
                }
                return;
            } catch (IOException | RuntimeException e) {
                InventoryBackups.LOGGER.error("Could not read the backups of {} for {}, setting the file aside", player, day, e);
                DayFiles.setAside(DayFiles.file(dir, day));
            }
        }
    }

    /**
     * Applies a capture and appends an entry for it. Returns null when nothing was written: the capture changed nothing
     * and its trigger is not a marker worth keeping.
     */
    @Nullable
    Appended append(Capture capture, HolderLookup.Provider registries, int keyframeInterval) throws IOException {
        ensureLoaded();
        Int2ObjectMap<StackRef> next = new Int2ObjectOpenHashMap<>();
        if (capture.full()) {
            if (!capture.ender()) {
                state.int2ObjectEntrySet().forEach(e -> {
                    if (Slots.isEnderChest(e.getIntKey())) {
                        next.put(e.getIntKey(), e.getValue());
                    }
                });
            }
        } else {
            next.putAll(state);
        }
        Long2ObjectMap<CompoundTag> newItems = new Long2ObjectOpenHashMap<>();
        for (Int2ObjectMap.Entry<ItemStack> change : capture.changes().int2ObjectEntrySet()) {
            ItemStack stack = change.getValue();
            if (stack.isEmpty()) {
                next.remove(change.getIntKey());
                continue;
            }
            LogFile.PoolItem item = ItemCodec.encode(registries, stack);
            newItems.put(item.hash(), item.tag());
            next.put(change.getIntKey(), new StackRef(item.hash(), stack.getCount()));
        }

        IntSet changedSlots = new IntOpenHashSet(next.keySet());
        changedSlots.addAll(state.keySet());
        changedSlots.removeIf((int slot) -> Objects.equals(state.get(slot), next.get(slot)));
        if (changedSlots.isEmpty() && !capture.trigger().recordUnchanged()) {
            return null;
        }

        long time = Math.max(capture.time(), lastTime == Long.MIN_VALUE ? capture.time() : lastTime + 1);
        LocalDate day = DayFiles.day(time);
        boolean newFile = open == null || !open.day().equals(day);
        if (newFile) {
            open = new DayLog(day, 0);
            openLength = 0;
        }
        boolean keyframe = newFile || sinceKeyframe >= keyframeInterval;
        Int2ObjectMap<StackRef> written = keyframe ? next : changedOnly(next, changedSlots);

        items.putAll(newItems);
        List<LogFile.PoolItem> framePool = new ArrayList<>();
        IntArrayList slots = new IntArrayList();
        IntArrayList refs = new IntArrayList();
        IntArrayList counts = new IntArrayList();
        int[] sorted = keyframe ? next.keySet().toIntArray() : changedSlots.toIntArray();
        Arrays.sort(sorted);
        for (int slot : sorted) {
            StackRef ref = written.get(slot);
            slots.add(slot);
            if (ref == null) {
                refs.add(-1);
                counts.add(0);
                continue;
            }
            int index = open.indexOf(ref.hash());
            if (index < 0) {
                CompoundTag tag = Objects.requireNonNull(items.get(ref.hash()), "pool item");
                index = open.addToPool(ref.hash(), tag);
                framePool.add(new LogFile.PoolItem(ref.hash(), tag));
            }
            refs.add(index);
            counts.add(ref.count());
        }
        Entry entry = new Entry(time, capture.trigger(), capture.context(), keyframe, changedSlots.size(), slots.toIntArray(),
                refs.toIntArray(), counts.toIntArray());
        Path file = DayFiles.file(dir, day);
        int bytes = LogFile.append(file, openLength, open.thin(), framePool, List.of(entry));
        openLength = (openLength < 8 ? 0 : openLength) + bytes;
        open.addEntry(entry);

        Int2ObjectMap<StackRef> before = state;
        Long2ObjectMap<CompoundTag> involved = new Long2ObjectOpenHashMap<>(items);
        state = next;
        lastTime = time;
        sinceKeyframe = keyframe ? 0 : sinceKeyframe + 1;
        LongOpenHashSet live = new LongOpenHashSet();
        state.values().forEach(ref -> live.add(ref.hash()));
        items.keySet().removeIf((long hash) -> !live.contains(hash));
        return new Appended(entry, bytes, before, state, involved);
    }

    private static Int2ObjectMap<StackRef> changedOnly(Int2ObjectMap<StackRef> next, IntSet changed) {
        Int2ObjectMap<StackRef> result = new Int2ObjectOpenHashMap<>();
        for (int slot : changed) {
            StackRef ref = next.get(slot);
            if (ref != null) {
                result.put(slot, ref);
            }
        }
        return result;
    }
}
