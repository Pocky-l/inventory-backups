package com.pockyl.inventorybackups.index;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.nbt.CompoundTag;

/**
 * When one player held one item: the time ranges, the count now and the largest count seen. Ranges are stored as
 * {@code [from, to]} pairs; the newest range is open ({@link Long#MAX_VALUE}) while the player still holds the item, also
 * while they are offline.
 */
public final class Presence {
    /** Older ranges are merged beyond this many, keeping first and last seen exact. */
    private static final int MAX_RANGES = 32;

    private long first;
    private long last;
    private int count;
    private int max;
    private final LongArrayList ranges = new LongArrayList();

    public long first() {
        return first;
    }

    /** Time the player was last seen with the item; for current holders the time of the newest change. */
    public long last() {
        return last;
    }

    public int count() {
        return count;
    }

    public int max() {
        return max;
    }

    public boolean holdsNow() {
        return count > 0;
    }

    /** Ranges as {@code from, to} pairs, oldest first. */
    public long[] ranges() {
        return ranges.toLongArray();
    }

    /** Whether the player held the item at the time. */
    public boolean heldAt(long time) {
        for (int i = 0; i < ranges.size(); i += 2) {
            if (ranges.getLong(i) <= time && time <= ranges.getLong(i + 1)) {
                return true;
            }
        }
        return false;
    }

    void update(long time, int before, int after) {
        if (after > 0) {
            if (first == 0) {
                first = time;
            }
            last = time;
            max = Math.max(max, after);
            if (before <= 0 || ranges.isEmpty() || ranges.getLong(ranges.size() - 1) != Long.MAX_VALUE) {
                ranges.add(time);
                ranges.add(Long.MAX_VALUE);
                if (ranges.size() > MAX_RANGES * 2) {
                    // Merge the two oldest ranges into one.
                    ranges.set(1, ranges.getLong(3));
                    ranges.removeElements(2, 4);
                }
            }
        } else if (before > 0 && !ranges.isEmpty() && ranges.getLong(ranges.size() - 1) == Long.MAX_VALUE) {
            ranges.set(ranges.size() - 1, time);
            last = time;
        }
        count = after;
    }

    /** Drops ranges that ended before the cutoff; returns whether anything is left. */
    boolean prune(long cutoff) {
        while (ranges.size() >= 2 && ranges.getLong(1) < cutoff) {
            ranges.removeElements(0, 2);
        }
        if (!ranges.isEmpty() && first < ranges.getLong(0)) {
            first = ranges.getLong(0);
        }
        return !ranges.isEmpty() || count > 0;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("f", first);
        tag.putLong("l", last);
        tag.putInt("c", count);
        tag.putInt("m", max);
        tag.putLongArray("r", ranges.toLongArray());
        return tag;
    }

    static Presence load(CompoundTag tag) {
        Presence presence = new Presence();
        presence.first = tag.getLong("f");
        presence.last = tag.getLong("l");
        presence.count = tag.getInt("c");
        presence.max = tag.getInt("m");
        long[] ranges = tag.getLongArray("r");
        if (ranges.length % 2 == 0) {
            presence.ranges.addElements(0, ranges);
        }
        return presence;
    }
}
