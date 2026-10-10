package com.pockyl.inventorybackups.storage;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.InventoryBackups;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * All stored timelines of one world. Every file access happens on a single background thread: the main thread only
 * hands over {@link Capture}s and receives query results through futures.
 * <p>
 * Layout below the root: {@code players/<uuid>/<yyyy-MM-dd>.ibk} (see {@link LogFile}) and {@code players.dat}.
 */
public final class BackupStore {
    private static final int CACHED_DAYS = 96;
    private static final long DIRECTORY_SAVE_INTERVAL = 30_000;

    private final Path root;
    private final HolderLookup.Provider registries;
    private final IntSupplier keyframeInterval;
    private final ExecutorService io;
    private final Map<UUID, PlayerLog> logs = new HashMap<>();
    private final PlayerDirectory directory;
    private final List<Listener> listeners = new ArrayList<>();
    private final LinkedHashMap<Path, CachedDay> cache = new LinkedHashMap<>(CACHED_DAYS, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, CachedDay> eldest) {
            return size() > CACHED_DAYS;
        }
    };
    private final AtomicLong entriesWritten = new AtomicLong();
    private final AtomicLong bytesWritten = new AtomicLong();
    private final AtomicLong changeEntries = new AtomicLong();
    private final AtomicLong changeBytes = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private long lastDirectorySave;
    private volatile boolean closed;

    /** Called on the storage thread after each written entry. */
    public interface Listener {
        /**
         * @param items pool items of the stacks in {@code before} and {@code after}
         */
        void appended(UUID player, String name, Entry entry, Int2ObjectMap<StackRef> before, Int2ObjectMap<StackRef> after,
                      LongFunction<CompoundTag> items);
    }

    public BackupStore(Path root, HolderLookup.Provider registries, IntSupplier keyframeInterval) {
        this.root = root;
        this.registries = registries;
        this.keyframeInterval = keyframeInterval;
        this.directory = new PlayerDirectory(root.resolve("players.dat"));
        this.io = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Inventory Backups IO");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
        io.execute(directory::load);
    }

    public Path root() {
        return root;
    }

    public HolderLookup.Provider registries() {
        return registries;
    }

    public void addListener(Listener listener) {
        execute(() -> listeners.add(listener));
    }

    /** Queues a capture; returns immediately. */
    public void submit(Capture capture) {
        execute(() -> write(capture));
    }

    /** Runs a task on the storage thread; ignored after {@link #close()}. */
    public void execute(Runnable task) {
        if (closed) {
            return;
        }
        io.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                InventoryBackups.LOGGER.error("Inventory Backups storage task failed", e);
            }
        });
    }

    /** Computes a value on the storage thread. */
    public <T> CompletableFuture<T> call(Supplier<T> task) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Storage is closed"));
        }
        return CompletableFuture.supplyAsync(task, io);
    }

    /** Blocks until every queued task has run. */
    public void awaitIdle() {
        call(() -> Boolean.TRUE).join();
    }

    /** Finishes the queued writes and stops the storage thread. */
    public void close() {
        if (closed) {
            return;
        }
        execute(directory::save);
        closed = true;
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                InventoryBackups.LOGGER.warn("Inventory Backups storage did not finish writing within 30 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Storage thread only
    // ---------------------------------------------------------------------------------------------------------------

    public Path playersDir() {
        return root.resolve("players");
    }

    public Path playerDir(UUID player) {
        return playersDir().resolve(player.toString());
    }

    /** Players with a backup folder. */
    public List<UUID> storedPlayers() {
        List<UUID> players = new ArrayList<>();
        if (!Files.isDirectory(playersDir())) {
            return players;
        }
        try (Stream<Path> dirs = Files.list(playersDir())) {
            for (Path dir : dirs.toList()) {
                try {
                    players.add(UUID.fromString(dir.getFileName().toString()));
                } catch (IllegalArgumentException e) {
                    InventoryBackups.LOGGER.debug("Ignoring unexpected folder {}", dir);
                }
            }
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not list {}", playersDir(), e);
        }
        return players;
    }

    public List<PlayerInfo> playerInfos() {
        return directory.all();
    }

    @Nullable
    public PlayerInfo playerInfo(UUID player) {
        return directory.get(player);
    }

    void write(Capture capture) {
        PlayerLog log = log(capture.player());
        try {
            PlayerLog.Appended appended = log.append(capture, registries, Math.max(1, keyframeInterval.getAsInt()));
            if (appended == null) {
                skipped.incrementAndGet();
                return;
            }
            entriesWritten.incrementAndGet();
            bytesWritten.addAndGet(appended.bytes());
            if (capture.trigger() == Trigger.CHANGE) {
                changeEntries.incrementAndGet();
                changeBytes.addAndGet(appended.bytes());
            }
            directory.record(capture.player(), capture.name(), appended.entry().time(), capture.trigger());
            LongFunction<CompoundTag> items = appended.items()::get;
            for (Listener listener : listeners) {
                listener.appended(capture.player(), capture.name(), appended.entry(), appended.before(), appended.after(), items);
            }
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not write the backup of {}", capture.name(), e);
        }
        long now = System.currentTimeMillis();
        if (now - lastDirectorySave > DIRECTORY_SAVE_INTERVAL) {
            lastDirectorySave = now;
            directory.save();
        }
    }

    PlayerLog log(UUID player) {
        return logs.computeIfAbsent(player, uuid -> new PlayerLog(uuid, playerDir(uuid)));
    }

    /** Drops the cached write state and decoded files of a player after their files were rewritten or deleted. */
    void invalidate(UUID player) {
        PlayerLog log = logs.get(player);
        if (log != null) {
            log.invalidate();
        }
        cache.keySet().removeIf(path -> path.startsWith(playerDir(player)));
    }

    void forgetDirectoryEntry(UUID player) {
        directory.forget(player);
    }

    void saveDirectory() {
        directory.save();
    }

    /** Days with a stored file for the player, oldest first. */
    public List<LocalDate> days(UUID player) {
        return DayFiles.days(playerDir(player));
    }

    /** The decoded file of a day, from memory when possible; empty when there is none or it cannot be read. */
    public Optional<DayLog> day(UUID player, LocalDate day) {
        PlayerLog log = logs.get(player);
        DayLog open = log == null ? null : log.openLog(day);
        if (open != null) {
            return Optional.of(open);
        }
        Path file = DayFiles.file(playerDir(player), day);
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            long size = Files.size(file);
            long modified = Files.getLastModifiedTime(file).toMillis();
            CachedDay cached = cache.get(file);
            if (cached != null && cached.size == size && cached.modified == modified) {
                return Optional.of(cached.log);
            }
            DayLog read = LogFile.read(file, day).log();
            cache.put(file, new CachedDay(size, modified, read));
            return Optional.of(read);
        } catch (IOException | RuntimeException e) {
            InventoryBackups.LOGGER.error("Could not read {}", file, e);
            return Optional.empty();
        }
    }

    /** Entries of a player between two times (inclusive), oldest first. */
    public List<Entry> entries(UUID player, long from, long to) {
        List<Entry> result = new ArrayList<>();
        LocalDate first = DayFiles.day(from);
        LocalDate last = DayFiles.day(to);
        for (LocalDate day : days(player)) {
            if (day.isBefore(first) || day.isAfter(last)) {
                continue;
            }
            day(player, day).ifPresent(log -> {
                for (Entry entry : log.entries()) {
                    if (entry.time() >= from && entry.time() <= to) {
                        result.add(entry);
                    }
                }
            });
        }
        return result;
    }

    /** The newest entry at or before the time with the state after it and the state before it. */
    public Optional<StateView> stateAt(UUID player, long time) {
        List<LocalDate> days = days(player);
        for (int i = days.size() - 1; i >= 0; i--) {
            if (days.get(i).isAfter(DayFiles.day(time))) {
                continue;
            }
            Optional<DayLog> found = day(player, days.get(i));
            if (found.isEmpty()) {
                continue;
            }
            DayLog log = found.get();
            int index = log.indexAtOrBefore(time);
            if (index < 0) {
                continue;
            }
            Entry entry = log.entries().get(index);
            Int2ObjectMap<ItemStack> items = decode(log, log.stateAt(index));
            if (index > 0) {
                return Optional.of(new StateView(entry, items, log.entries().get(index - 1), decode(log, log.stateAt(index - 1))));
            }
            for (int j = i - 1; j >= 0; j--) {
                Optional<DayLog> before = day(player, days.get(j));
                if (before.isPresent() && !before.get().entries().isEmpty()) {
                    DayLog previous = before.get();
                    int last = previous.entries().size() - 1;
                    return Optional.of(new StateView(entry, items, previous.entries().get(last), decode(previous, previous.stateAt(last))));
                }
            }
            return Optional.of(new StateView(entry, items, null, new Int2ObjectOpenHashMap<>()));
        }
        return Optional.empty();
    }

    /** The current (newest) stored state of a player. */
    public Optional<StateView> latest(UUID player) {
        return stateAt(player, Long.MAX_VALUE);
    }

    public Int2ObjectMap<ItemStack> decode(DayLog log, Int2ObjectMap<StackRef> state) {
        Int2ObjectMap<ItemStack> items = new Int2ObjectOpenHashMap<>();
        for (Int2ObjectMap.Entry<StackRef> slot : state.int2ObjectEntrySet()) {
            int index = log.indexOf(slot.getValue().hash());
            if (index < 0) {
                continue;
            }
            ItemStack stack = ItemCodec.decode(registries, log.poolTag(index), slot.getValue().count());
            if (!stack.isEmpty()) {
                items.put(slot.getIntKey(), stack);
            }
        }
        return items;
    }

    /** Bytes written and entries recorded since the store was opened. */
    public Stats stats() {
        return new Stats(entriesWritten.get(), bytesWritten.get(), changeEntries.get(), changeBytes.get(), skipped.get());
    }

    /**
     * @param skipped captures that changed nothing and were dropped instead of stored as duplicates
     */
    public record Stats(long entries, long bytes, long changeEntries, long changeBytes, long skipped) {
        public long bytesPerChange() {
            return changeEntries == 0 ? 0 : changeBytes / changeEntries;
        }
    }

    private record CachedDay(long size, long modified, DayLog log) {
    }
}
