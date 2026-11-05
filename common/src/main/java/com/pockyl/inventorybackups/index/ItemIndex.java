package com.pockyl.inventorybackups.index;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.DayLog;
import com.pockyl.inventorybackups.storage.Entry;
import com.pockyl.inventorybackups.storage.LogFile;
import com.pockyl.inventorybackups.storage.StackRef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongFunction;

/**
 * Which player held which item when, for every player, online or offline. Kept up to date from every written entry,
 * saved to {@code index.dat} and rebuilt from the stored timelines when that file is missing. Two kinds of keys: every
 * item id ({@code i:minecraft:diamond}) and the fingerprint of every distinctive stack ({@code f:<hex>}), see
 * {@link ItemKeys}. Owned by the storage thread.
 */
public final class ItemIndex implements BackupStore.Listener {
    private static final int VERSION = 1;
    private static final int CACHED_HASHES = 65_536;
    private static final long SAVE_INTERVAL = 5 * 60_000L;

    private final Path file;
    private final HolderLookup.Provider registries;
    private final Map<String, KeyEntry> keys = new HashMap<>();
    /** Keys of recently seen pool items, so the NBT of an item is not parsed again for every entry. */
    private final Long2ObjectLinkedOpenHashMap<ItemKeys> hashKeys = new Long2ObjectLinkedOpenHashMap<>();
    private boolean dirty;
    private long lastSave = System.currentTimeMillis();
    /** Time of the newest indexed entry; a saved index older than the storage (after a crash) is rebuilt. */
    private long watermark;

    /** One key: what it stands for and who held it. */
    public static final class KeyEntry {
        private final String key;
        private final String itemId;
        @Nullable
        private final String name;
        private final List<String> enchantments;
        @Nullable
        private final CompoundTag sample;
        private final Map<UUID, Presence> holders = new HashMap<>();

        KeyEntry(String key, String itemId, @Nullable String name, List<String> enchantments, @Nullable CompoundTag sample) {
            this.key = key;
            this.itemId = itemId;
            this.name = name;
            this.enchantments = enchantments;
            this.sample = sample;
        }

        public String key() {
            return key;
        }

        public String itemId() {
            return itemId;
        }

        @Nullable
        public String name() {
            return name;
        }

        public List<String> enchantments() {
            return enchantments;
        }

        /** NBT of one stack of this key, for showing it. */
        @Nullable
        public CompoundTag sample() {
            return sample;
        }

        public boolean isFingerprint() {
            return key.startsWith(ItemKeys.FINGERPRINT_PREFIX);
        }

        public Map<UUID, Presence> holders() {
            return holders;
        }
    }

    public ItemIndex(Path file, HolderLookup.Provider registries) {
        this.file = file;
        this.registries = registries;
    }

    /** Loads the saved index; rebuilds it from the timelines when it is missing or unreadable. */
    public void loadOrRebuild(BackupStore store) {
        if (load() && store.playerInfos().stream().allMatch(info -> info.lastEntry() <= watermark)) {
            return;
        }
        long start = System.currentTimeMillis();
        rebuild(store);
        save();
        InventoryBackups.LOGGER.info("Rebuilt the item index from the stored timelines: {} keys in {} ms", keys.size(),
                System.currentTimeMillis() - start);
    }

    @Override
    public void appended(UUID player, String name, Entry entry, Int2ObjectMap<StackRef> before, Int2ObjectMap<StackRef> after,
                         LongFunction<CompoundTag> items) {
        update(player, entry.time(), before, after, items);
        watermark = Math.max(watermark, entry.time());
        if (dirty && System.currentTimeMillis() - lastSave > SAVE_INTERVAL) {
            save();
        }
    }

    @Override
    public void closing() {
        save();
    }

    public Collection<KeyEntry> keys() {
        return keys.values();
    }

    @Nullable
    public KeyEntry get(String key) {
        return keys.get(key);
    }

    /** Item ids that were ever stored, for completion. */
    public Set<String> itemIds() {
        Set<String> ids = new HashSet<>();
        for (KeyEntry entry : keys.values()) {
            ids.add(entry.itemId());
        }
        return ids;
    }

    /** Forgets ranges that ended before the cutoff and keys nobody holds any more. */
    public void prune(long cutoff) {
        keys.values().removeIf(entry -> {
            entry.holders.values().removeIf(presence -> !presence.prune(cutoff));
            return entry.holders.isEmpty();
        });
        dirty = true;
    }

    private void update(UUID player, long time, Int2ObjectMap<StackRef> before, Int2ObjectMap<StackRef> after,
                        LongFunction<CompoundTag> items) {
        Map<String, KeyEntry> touched = new HashMap<>();
        Object2IntMap<String> was = counts(before, items, touched);
        Object2IntMap<String> now = counts(after, items, touched);
        Set<String> all = new HashSet<>(was.keySet());
        all.addAll(now.keySet());
        for (String key : all) {
            int previous = was.getInt(key);
            int current = now.getInt(key);
            KeyEntry entry = keys.get(key);
            if (entry == null) {
                entry = touched.get(key);
                if (entry == null) {
                    continue;
                }
                keys.put(key, entry);
            }
            Presence presence = entry.holders.computeIfAbsent(player, uuid -> new Presence());
            presence.update(time, previous, current);
            dirty = true;
        }
    }

    /** Count of every key in a state; creates entries for keys not indexed yet into {@code created}. */
    private Object2IntMap<String> counts(Int2ObjectMap<StackRef> state, LongFunction<CompoundTag> items, Map<String, KeyEntry> created) {
        Object2IntMap<String> counts = new Object2IntOpenHashMap<>();
        for (StackRef ref : state.values()) {
            ItemKeys itemKeys = keysOf(ref.hash(), items);
            if (itemKeys == null) {
                continue;
            }
            counts.mergeInt(itemKeys.itemKey(), ref.count(), Integer::sum);
            ensure(itemKeys.itemKey(), itemKeys, ref.hash(), items, created);
            String fingerprint = itemKeys.fingerprintKey();
            if (fingerprint != null) {
                counts.mergeInt(fingerprint, ref.count(), Integer::sum);
                ensure(fingerprint, itemKeys, ref.hash(), items, created);
            }
        }
        return counts;
    }

    private void ensure(String key, ItemKeys itemKeys, long hash, LongFunction<CompoundTag> items, Map<String, KeyEntry> created) {
        if (keys.containsKey(key) || created.containsKey(key)) {
            return;
        }
        boolean fingerprint = key.startsWith(ItemKeys.FINGERPRINT_PREFIX);
        CompoundTag sample = items.apply(hash);
        created.put(key, new KeyEntry(key, itemKeys.itemId(), fingerprint ? itemKeys.name() : null,
                fingerprint ? itemKeys.enchantments() : List.of(), sample == null ? null : sample.copy()));
    }

    @Nullable
    private ItemKeys keysOf(long hash, LongFunction<CompoundTag> items) {
        ItemKeys cached = hashKeys.getAndMoveToLast(hash);
        if (cached != null) {
            return cached;
        }
        CompoundTag tag = items.apply(hash);
        if (tag == null) {
            return null;
        }
        ItemKeys computed = ItemKeys.of(tag, registries);
        hashKeys.putAndMoveToLast(hash, computed);
        if (hashKeys.size() > CACHED_HASHES) {
            hashKeys.removeFirst();
        }
        return computed;
    }

    /** Replays every stored timeline in time order. */
    private void rebuild(BackupStore store) {
        keys.clear();
        watermark = 0;
        for (UUID player : store.storedPlayers()) {
            Int2ObjectMap<StackRef> state = new Int2ObjectOpenHashMap<>();
            Long2ObjectMap<CompoundTag> tags = new Long2ObjectOpenHashMap<>();
            for (LocalDate day : store.days(player)) {
                DayLog log = store.day(player, day).orElse(null);
                if (log == null) {
                    continue;
                }
                for (Entry entry : log.entries()) {
                    Int2ObjectMap<StackRef> next = new Int2ObjectOpenHashMap<>(state);
                    log.apply(next, entry);
                    for (StackRef ref : next.values()) {
                        int index = log.indexOf(ref.hash());
                        if (index >= 0) {
                            tags.put(ref.hash(), log.poolTag(index));
                        }
                    }
                    update(player, entry.time(), state, next, tags::get);
                    watermark = Math.max(watermark, entry.time());
                    state = next;
                }
            }
        }
        dirty = true;
    }

    private boolean load() {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            CompoundTag root = LogFile.readCompound(file);
            if (root.getInt("v") != VERSION) {
                return false;
            }
            keys.clear();
            watermark = root.getLong("t");
            for (Tag tag : root.getList("k", Tag.TAG_COMPOUND)) {
                CompoundTag key = (CompoundTag) tag;
                List<String> enchantments = new ArrayList<>();
                for (Tag enchantment : key.getList("e", Tag.TAG_STRING)) {
                    enchantments.add(enchantment.getAsString());
                }
                KeyEntry entry = new KeyEntry(key.getString("k"), key.getString("i"), key.contains("n") ? key.getString("n") : null,
                        List.copyOf(enchantments), key.contains("s") ? key.getCompound("s") : null);
                for (Tag holder : key.getList("h", Tag.TAG_COMPOUND)) {
                    CompoundTag presence = (CompoundTag) holder;
                    entry.holders.put(presence.getUUID("u"), Presence.load(presence));
                }
                keys.put(entry.key(), entry);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            InventoryBackups.LOGGER.warn("Could not read the item index {}, rebuilding it", file, e);
            return false;
        }
    }

    public void save() {
        if (!dirty) {
            return;
        }
        ListTag list = new ListTag();
        for (KeyEntry entry : keys.values()) {
            CompoundTag key = new CompoundTag();
            key.putString("k", entry.key());
            key.putString("i", entry.itemId());
            if (entry.name() != null) {
                key.putString("n", entry.name());
            }
            if (!entry.enchantments().isEmpty()) {
                ListTag enchantments = new ListTag();
                entry.enchantments().forEach(id -> enchantments.add(StringTag.valueOf(id)));
                key.put("e", enchantments);
            }
            if (entry.sample() != null) {
                key.put("s", entry.sample());
            }
            ListTag holders = new ListTag();
            entry.holders().forEach((uuid, presence) -> {
                CompoundTag holder = presence.save();
                holder.putUUID("u", uuid);
                holders.add(holder);
            });
            key.put("h", holders);
            list.add(key);
        }
        CompoundTag root = new CompoundTag();
        root.putInt("v", VERSION);
        root.putLong("t", watermark);
        root.put("k", list);
        try {
            LogFile.writeCompound(file, root);
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not save the item index {}", file, e);
        }
    }
}
