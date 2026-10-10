package com.pockyl.inventorybackups.storage;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.nbt.CompoundTag;

import com.pockyl.inventorybackups.InventoryBackups;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Thins out old entries and enforces the disk budgets. Runs on the storage thread.
 * <p>
 * Every change is kept for {@code keepAll}; then the last state of each hour until {@code hourly}; then the last state
 * of each day until {@code daily}; deaths and admin actions until {@code important}. The newest entry of a player is
 * never removed, so the last known state survives any inactivity. A file records how far it has been thinned, so each
 * file is rewritten only a few times in its life, and the rewrite also compacts its frames into one.
 */
public final class Retention {
    /** Thin levels, by the age of an entry. */
    private static final int KEEP_ALL = 0;
    private static final int HOURLY = 1;
    private static final int DAILY = 2;
    private static final int IMPORTANT_ONLY = 3;
    private static final int EXPIRED = 4;
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24 * HOUR;

    private Retention() {
    }

    /**
     * @param playerBudget bytes per player, 0 for no limit
     * @param totalBudget  bytes for all players, 0 for no limit
     */
    public record Policy(long keepAll, long hourly, long daily, long important, long playerBudget, long totalBudget,
                         int keyframeInterval) {
        public static Policy ofDays(int keepAllHours, int hourlyDays, int dailyDays, int importantDays, long playerBudget,
                                    long totalBudget, int keyframeInterval) {
            return new Policy(keepAllHours * HOUR, hourlyDays * DAY, dailyDays * DAY, importantDays * DAY, playerBudget, totalBudget,
                    keyframeInterval);
        }

        int level(long age) {
            if (age < keepAll) {
                return KEEP_ALL;
            }
            if (age < hourly) {
                return HOURLY;
            }
            if (age < daily) {
                return DAILY;
            }
            return age < important ? IMPORTANT_ONLY : EXPIRED;
        }
    }

    public record Result(int rewritten, int deleted, int entriesRemoved, long bytesFreed) {
        Result plus(Result other) {
            return new Result(rewritten + other.rewritten, deleted + other.deleted, entriesRemoved + other.entriesRemoved,
                    bytesFreed + other.bytesFreed);
        }
    }

    public static Result run(BackupStore store, Policy policy, long now) {
        Result total = new Result(0, 0, 0, 0);
        for (UUID player : store.storedPlayers()) {
            try {
                total = total.plus(thin(store, player, policy, now));
            } catch (IOException | RuntimeException e) {
                InventoryBackups.LOGGER.error("Retention failed for the backups of {}", player, e);
            }
        }
        total = total.plus(enforceTotalBudget(store, policy.totalBudget()));
        for (UUID player : store.storedPlayers()) {
            Path dir = store.playerDir(player);
            DayFiles.deleteIfEmpty(dir);
            if (!Files.isDirectory(dir)) {
                store.forgetPlayer(player);
            }
        }
        DayFiles.deleteIfEmpty(store.playersDir());
        store.saveDirectory();
        return total;
    }

    private static Result thin(BackupStore store, UUID player, Policy policy, long now) throws IOException {
        Path dir = store.playerDir(player);
        List<LocalDate> days = DayFiles.days(dir);
        if (days.isEmpty()) {
            return new Result(0, 0, 0, 0);
        }
        int rewritten = 0;
        int deleted = 0;
        int removed = 0;
        long freed = 0;
        LocalDate newest = days.get(days.size() - 1);
        LocalDate today = DayFiles.day(now);
        for (LocalDate day : days) {
            Path file = DayFiles.file(dir, day);
            long start = DayFiles.startOf(day);
            int oldestLevel = policy.level(now - start);
            int newestLevel = policy.level(now - (start + DAY - 1));
            if (newestLevel == EXPIRED && !day.equals(newest)) {
                long size = Files.size(file);
                Files.delete(file);
                deleted++;
                freed += size;
                continue;
            }
            LogFile.Read read;
            try {
                read = readIfNeeded(file, day, oldestLevel, day.isBefore(today));
            } catch (IOException e) {
                InventoryBackups.LOGGER.error("Retention skips unreadable file {}", file, e);
                continue;
            }
            if (read == null) {
                continue;
            }
            DayLog log = read.log();
            IntList kept = select(log, policy, now, day.equals(newest));
            long size = Files.size(file);
            if (kept.isEmpty()) {
                Files.delete(file);
                deleted++;
                removed += log.entries().size();
                freed += size;
                continue;
            }
            boolean compact = read.frames() > 1 && day.isBefore(today);
            if (kept.size() == log.entries().size() && !compact && log.thin() >= newestLevel) {
                continue;
            }
            DayLog thinned = rebuild(log, kept, policy.keyframeInterval());
            thinned.setThin(Math.max(log.thin(), newestLevel));
            long written = LogFile.rewrite(file, thinned, pool(thinned));
            rewritten++;
            removed += log.entries().size() - kept.size();
            freed += size - written;
        }
        Result result = new Result(rewritten, deleted, removed, freed).plus(enforcePlayerBudget(dir, policy.playerBudget()));
        if (result.rewritten() + result.deleted() > 0) {
            store.invalidate(player);
        }
        return result;
    }

    /**
     * Reads a file unless its header shows it needs no work: thinned as far as its oldest entry requires and already
     * compacted. Returns null for files to skip.
     */
    private static LogFile.Read readIfNeeded(Path file, LocalDate day, int oldestLevel, boolean closed) throws IOException {
        if (oldestLevel == KEEP_ALL) {
            return null;
        }
        LogFile.Header header = LogFile.peek(file);
        if (header.thin() >= oldestLevel && (header.frames() <= 1 || !closed)) {
            return null;
        }
        return LogFile.read(file, day);
    }

    /** Indices of the entries to keep. */
    private static IntList select(DayLog log, Policy policy, long now, boolean newestFile) {
        List<Entry> entries = log.entries();
        IntList kept = new IntArrayList();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            long age = now - entry.time();
            int level = policy.level(age);
            boolean lastOfFile = i == entries.size() - 1;
            boolean keep = switch (level) {
                case KEEP_ALL -> true;
                case HOURLY -> lastOfFile || entries.get(i + 1).time() / HOUR != entry.time() / HOUR;
                case DAILY -> lastOfFile;
                default -> false;
            };
            if (entry.trigger().important() && level < EXPIRED) {
                keep = true;
            }
            if (newestFile && lastOfFile) {
                keep = true;
            }
            if (keep) {
                kept.add(i);
            }
        }
        return kept;
    }

    /** A log with only the kept entries: the first one becomes a keyframe, the others store changes between kept states. */
    static DayLog rebuild(DayLog old, IntList kept, int keyframeInterval) {
        DayLog log = new DayLog(old.day(), old.thin());
        Int2ObjectMap<StackRef> previous = null;
        int sinceKeyframe = 0;
        for (int index : kept) {
            Entry entry = old.entries().get(index);
            Int2ObjectMap<StackRef> state = old.stateAt(index);
            boolean keyframe = previous == null || sinceKeyframe >= keyframeInterval;
            IntSet changed = new IntOpenHashSet(state.keySet());
            int changedCount = entry.changed();
            if (previous != null) {
                changed.addAll(previous.keySet());
                Int2ObjectMap<StackRef> before = previous;
                changed.removeIf((int slot) -> Objects.equals(before.get(slot), state.get(slot)));
                changedCount = changed.size();
            }
            int[] slots = (keyframe ? state.keySet() : changed).toIntArray();
            Arrays.sort(slots);
            int[] refs = new int[slots.length];
            int[] counts = new int[slots.length];
            for (int i = 0; i < slots.length; i++) {
                StackRef ref = state.get(slots[i]);
                if (ref == null) {
                    refs[i] = -1;
                    continue;
                }
                CompoundTag tag = old.poolTag(old.indexOf(ref.hash()));
                refs[i] = log.addToPool(ref.hash(), tag);
                counts[i] = ref.count();
            }
            log.addEntry(new Entry(entry.time(), entry.trigger(), entry.context(), keyframe, changedCount, slots, refs, counts));
            previous = state;
            sinceKeyframe = keyframe ? 0 : sinceKeyframe + 1;
        }
        return log;
    }

    private static List<LogFile.PoolItem> pool(DayLog log) {
        List<LogFile.PoolItem> pool = new ArrayList<>(log.poolSize());
        for (int i = 0; i < log.poolSize(); i++) {
            pool.add(new LogFile.PoolItem(log.poolHash(i), log.poolTag(i)));
        }
        return pool;
    }

    /** Deletes the oldest days of a player beyond the budget, never the newest one. */
    private static Result enforcePlayerBudget(Path dir, long budget) throws IOException {
        if (budget <= 0) {
            return new Result(0, 0, 0, 0);
        }
        List<LocalDate> days = DayFiles.days(dir);
        long total = 0;
        for (LocalDate day : days) {
            total += Files.size(DayFiles.file(dir, day));
        }
        int deleted = 0;
        long freed = 0;
        for (int i = 0; i < days.size() - 1 && total > budget; i++) {
            Path file = DayFiles.file(dir, days.get(i));
            long size = Files.size(file);
            Files.delete(file);
            total -= size;
            freed += size;
            deleted++;
        }
        return new Result(0, deleted, 0, freed);
    }

    /** Deletes the oldest days across all players beyond the budget, never a player's newest day. */
    private static Result enforceTotalBudget(BackupStore store, long budget) {
        if (budget <= 0) {
            return new Result(0, 0, 0, 0);
        }
        record Candidate(UUID player, LocalDate day, Path file, long size) {
        }
        List<Candidate> candidates = new ArrayList<>();
        long total = 0;
        for (UUID player : store.storedPlayers()) {
            Path dir = store.playerDir(player);
            List<LocalDate> days = DayFiles.days(dir);
            for (int i = 0; i < days.size(); i++) {
                Path file = DayFiles.file(dir, days.get(i));
                try {
                    long size = Files.size(file);
                    total += size;
                    if (i < days.size() - 1) {
                        candidates.add(new Candidate(player, days.get(i), file, size));
                    }
                } catch (IOException e) {
                    InventoryBackups.LOGGER.warn("Could not size {}", file, e);
                }
            }
        }
        candidates.sort(Comparator.comparing(Candidate::day));
        int deleted = 0;
        long freed = 0;
        for (Candidate candidate : candidates) {
            if (total <= budget) {
                break;
            }
            try {
                Files.delete(candidate.file());
                store.invalidate(candidate.player());
                total -= candidate.size();
                freed += candidate.size();
                deleted++;
            } catch (IOException e) {
                InventoryBackups.LOGGER.warn("Could not delete {}", candidate.file(), e);
            }
        }
        return new Result(0, deleted, 0, freed);
    }
}
