package com.pockyl.inventorybackups.client.screen;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.client.ClientConfig;
import com.pockyl.inventorybackups.network.EntryRow;
import com.pockyl.inventorybackups.network.Payloads;
import com.pockyl.inventorybackups.network.PlayerRow;
import com.pockyl.inventorybackups.storage.Trigger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the timeline tab shows: the player, the visible time window and its entries, the selected entry, the A/B pins
 * and a cache of loaded states.
 */
final class TimelineModel {
    static final long MINUTE = 60_000L;
    static final long HOUR = 60 * MINUTE;
    static final long DAY = 24 * HOUR;
    static final long MIN_SPAN = 10 * MINUTE;
    static final long MAX_SPAN = 365 * DAY;
    private static final int CACHED_STATES = 64;

    enum Base {
        PREVIOUS, A, B
    }

    @Nullable
    PlayerRow player;
    long span = DAY;
    long from;
    long to;
    List<EntryRow> entries = List.of();
    long first;
    long last;
    boolean truncated;
    boolean loading;
    int index = -1;
    @Nullable
    Long pinA;
    @Nullable
    Long pinB;
    Base base = Base.PREVIOUS;
    final Map<Long, Payloads.StateResponse> states = new LinkedHashMap<>(CACHED_STATES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Payloads.StateResponse> eldest) {
            return size() > CACHED_STATES;
        }
    };

    void reset(PlayerRow newPlayer) {
        player = newPlayer;
        entries = List.of();
        index = -1;
        pinA = null;
        pinB = null;
        base = Base.PREVIOUS;
        states.clear();
        first = 0;
        last = 0;
        span = spanOf(ClientConfig.DEFAULT_ZOOM.get());
    }

    static long spanOf(ClientConfig.Zoom zoom) {
        return switch (zoom) {
            case HOUR -> HOUR;
            case DAY -> DAY;
            case WEEK -> 7 * DAY;
            case ALL -> 0;
        };
    }

    /** Places the window so it shows {@code anchor}, never reaching beyond now. A span of 0 shows everything. */
    void centerOn(long anchor) {
        long now = System.currentTimeMillis();
        if (span <= 0) {
            from = first > 0 ? first - MINUTE : now - DAY;
            to = Math.max(last, now);
            return;
        }
        to = Math.min(now + MINUTE, anchor + span / 2);
        from = to - span;
        if (anchor < from) {
            from = anchor - span / 2;
            to = from + span;
        }
    }

    /**
     * Zooms to the whole stored history when it is much shorter than the window, so a short history does not end up
     * squeezed into a few pixels. Returns whether the window changed.
     */
    boolean fit() {
        long range = last - first;
        if (first <= 0 || span <= 0 || range >= span / 4) {
            return false;
        }
        long padding = Math.max(MINUTE, range / 10);
        from = first - padding;
        to = last + padding;
        span = to - from;
        return true;
    }

    void zoom(double factor, long around) {
        long current = to - from;
        long next = Math.max(MIN_SPAN, Math.min(MAX_SPAN, (long) (current * factor)));
        double position = current <= 0 ? 0.5 : (double) (around - from) / current;
        from = around - (long) (next * position);
        to = from + next;
        span = next;
    }

    void pan(double fraction) {
        long shift = (long) ((to - from) * fraction);
        long now = System.currentTimeMillis();
        if (to + shift > now + MINUTE) {
            shift = now + MINUTE - to;
        }
        from += shift;
        to += shift;
    }

    void setEntries(Payloads.TimelineResponse response) {
        long keep = selectedTime();
        entries = response.entries();
        first = response.first();
        last = response.last();
        truncated = response.truncated();
        index = keep == Long.MIN_VALUE ? entries.size() - 1 : indexAtOrBefore(keep);
        if (index < 0 && !entries.isEmpty()) {
            index = 0;
        }
    }

    @Nullable
    EntryRow selected() {
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    long selectedTime() {
        EntryRow row = selected();
        return row == null ? Long.MIN_VALUE : row.time();
    }

    int indexAtOrBefore(long time) {
        int found = -1;
        for (int i = 0; i < entries.size() && entries.get(i).time() <= time; i++) {
            found = i;
        }
        return found;
    }

    /** Entry nearest to a time, for clicks and drags on the track. */
    int nearest(long time) {
        int best = -1;
        long distance = Long.MAX_VALUE;
        for (int i = 0; i < entries.size(); i++) {
            long d = Math.abs(entries.get(i).time() - time);
            if (d < distance) {
                distance = d;
                best = i;
            }
        }
        return best;
    }

    /** Index of the newest death before the selected entry, or the newest death at all; -1 when there is none. */
    int previousDeath() {
        for (int i = index - 1; i >= 0; i--) {
            if (entries.get(i).trigger() == Trigger.DEATH) {
                return i;
            }
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).trigger() == Trigger.DEATH) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    Payloads.StateResponse current() {
        EntryRow row = selected();
        return row == null ? null : states.get(row.time());
    }

    /** Time whose state the current one is compared with, or null to compare with the previous entry. */
    @Nullable
    Long baseTime() {
        return switch (base) {
            case PREVIOUS -> null;
            case A -> pinA;
            case B -> pinB;
        };
    }

    /** Items of the state the current one is compared with; null while it is not loaded yet. */
    @Nullable
    Int2ObjectMap<ItemStack> baseItems() {
        Payloads.StateResponse state = current();
        if (state == null) {
            return null;
        }
        Long time = baseTime();
        if (time == null) {
            return state.previous() == null ? new Int2ObjectOpenHashMap<>() : state.previousItems();
        }
        Payloads.StateResponse pinned = states.get(time);
        return pinned == null ? null : pinned.items();
    }

    /** The loaded entry at an exact time, for slot history in tooltips. */
    @Nullable
    EntryRow entryAt(long time) {
        for (EntryRow row : entries) {
            if (row.time() == time) {
                return row;
            }
        }
        return null;
    }
}
