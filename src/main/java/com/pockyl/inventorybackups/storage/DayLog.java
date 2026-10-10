package com.pockyl.inventorybackups.storage;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.nbt.CompoundTag;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The decoded contents of one day file: the item pool (every distinct item stored once, without count) and the entries
 * in time order. The first entry of a file is always a keyframe, so every file can be read on its own.
 */
public final class DayLog {
    private final LocalDate day;
    private int thin;
    private final List<CompoundTag> poolTags = new ArrayList<>();
    private final LongArrayList poolHashes = new LongArrayList();
    private final Long2IntOpenHashMap hashIndex = new Long2IntOpenHashMap();
    private final List<Entry> entries = new ArrayList<>();

    public DayLog(LocalDate day, int thin) {
        this.day = day;
        this.thin = thin;
        hashIndex.defaultReturnValue(-1);
    }

    public LocalDate day() {
        return day;
    }

    /** How far retention has thinned this file, see {@link Retention}. */
    public int thin() {
        return thin;
    }

    void setThin(int thin) {
        this.thin = thin;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int poolSize() {
        return poolTags.size();
    }

    public CompoundTag poolTag(int index) {
        return poolTags.get(index);
    }

    public long poolHash(int index) {
        return poolHashes.getLong(index);
    }

    /** Pool index of an item hash, or -1. */
    public int indexOf(long hash) {
        return hashIndex.get(hash);
    }

    /** Adds an item to the pool unless it is already there and returns its index. */
    int addToPool(long hash, CompoundTag tag) {
        int index = hashIndex.get(hash);
        if (index >= 0) {
            return index;
        }
        index = poolTags.size();
        poolTags.add(tag);
        poolHashes.add(hash);
        hashIndex.put(hash, index);
        return index;
    }

    void addEntry(Entry entry) {
        entries.add(entry);
    }

    /** Index of the newest entry at or before the time, or -1. */
    public int indexAtOrBefore(long time) {
        int low = 0;
        int high = entries.size() - 1;
        int found = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (entries.get(mid).time() <= time) {
                found = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return found;
    }

    /** Full state after the entry at the index, by slot. */
    public Int2ObjectMap<StackRef> stateAt(int index) {
        int start = index;
        while (start > 0 && !entries.get(start).keyframe()) {
            start--;
        }
        Int2ObjectMap<StackRef> state = new Int2ObjectOpenHashMap<>();
        for (int i = start; i <= index; i++) {
            apply(state, entries.get(i));
        }
        return state;
    }

    /** Applies an entry of this log to a state. */
    public void apply(Int2ObjectMap<StackRef> state, Entry entry) {
        if (entry.keyframe()) {
            state.clear();
        }
        for (int i = 0; i < entry.slots().length; i++) {
            int ref = entry.refs()[i];
            if (ref < 0) {
                state.remove(entry.slots()[i]);
            } else {
                state.put(entry.slots()[i], new StackRef(poolHashes.getLong(ref), entry.counts()[i]));
            }
        }
    }
}
