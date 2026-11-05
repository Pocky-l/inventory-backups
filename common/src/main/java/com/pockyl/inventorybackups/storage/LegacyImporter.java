package com.pockyl.inventorybackups.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.Slots;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * One-time import of the backups of the older versions for Minecraft 1.20.1:
 * <ul>
 *     <li>1.2.x: {@code InventoryLog/inventory|enderchest/<uuid>/<yyyy-MM-dd-HH-mm-ss>[-type].json}, each holding the
 *     non-empty slots as SNBT;</li>
 *     <li>2.0.x: {@code invbackup/<uuid>/save_<epoch millis>.nbt}, compressed NBT with the inventory in the layout of a
 *     player file, the ender chest and the player's Forge capabilities, of which the Curios slots are taken.</li>
 * </ul>
 * All files of a player are merged into one timeline in time order. A marker file in the storage root makes sure the
 * import runs once; afterwards the old folders are kept, archived or deleted as configured. Runs on the storage thread.
 */
public final class LegacyImporter {
    public static final String MARKER = "legacy-imported.txt";
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final int NAME_LENGTH = 19;
    private static final String SNAPSHOT_PREFIX = "save_";
    private static final String SNAPSHOT_SUFFIX = ".nbt";
    /** Slot numbers of a player file's inventory list: 100-103 armor, 150 offhand. */
    private static final int FILE_ARMOR_START = 100;
    private static final int FILE_OFFHAND = 150;
    /** Backpacked 2 without Curios keeps the worn backpack in the inventory list, at this slot. */
    private static final int FILE_BACKPACK = 200;
    private static final String BACKPACK_KEY = "backpacked:back/0";
    private static final String CURIOS_CAPABILITY = "curios:inventory";

    private LegacyImporter() {
    }

    /**
     * @param curioSlots slots of 2.0.x snapshots skipped because their Curios data could not be read
     */
    public record Result(int players, int files, int skippedFiles, int curioSlots) {
    }

    private enum Kind {
        /** A 1.2.x inventory file. */
        INVENTORY,
        /** A 1.2.x ender chest file. */
        ENDER_CHEST,
        /** A 2.0.x snapshot with inventory, ender chest and Curios slots. */
        SNAPSHOT
    }

    private record LegacyFile(long time, Kind kind, String type, Path file) {
    }

    /** The parts of a 2.0.x snapshot; ender chest slots counted from 0. */
    private record Snapshot(String name, String reason, Int2ObjectMap<ItemStack> inventory, Int2ObjectMap<ItemStack> ender,
                            int skippedCurios) {
    }

    /**
     * Player names of 1.2.x files are not known here; the player browser fills them in from the server's profile cache.
     *
     * @param jsonRoot     the {@code InventoryLog} folder of 1.2.x
     * @param snapshotRoot the {@code invbackup} folder of 2.0.x
     * @return null when there was nothing to import or the import already ran
     */
    @Nullable
    public static Result run(BackupStore store, Path jsonRoot, Path snapshotRoot, Config.LegacyAction action) {
        Path marker = store.root().resolve(MARKER);
        boolean json = Files.isDirectory(jsonRoot);
        boolean snapshots = Files.isDirectory(snapshotRoot);
        if (Files.exists(marker) || !json && !snapshots) {
            return null;
        }
        Map<UUID, List<LegacyFile>> byPlayer = new HashMap<>();
        int skipped = 0;
        skipped += collect(jsonRoot.resolve("inventory"), Kind.INVENTORY, byPlayer);
        skipped += collect(jsonRoot.resolve("enderchest"), Kind.ENDER_CHEST, byPlayer);
        skipped += collectSnapshots(snapshotRoot, byPlayer);

        int files = 0;
        int players = 0;
        int curioSlots = 0;
        for (Map.Entry<UUID, List<LegacyFile>> player : byPlayer.entrySet()) {
            UUID uuid = player.getKey();
            if (!store.days(uuid).isEmpty()) {
                // Imported entries are older than anything new, and a timeline only grows forward.
                InventoryBackups.LOGGER.warn("Not importing old backups of {}: the player already has 3.x backups", uuid);
                continue;
            }
            List<LegacyFile> list = player.getValue();
            list.sort(Comparator.comparingLong(LegacyFile::time));
            Int2ObjectMap<ItemStack> inventory = new Int2ObjectOpenHashMap<>();
            Int2ObjectMap<ItemStack> ender = new Int2ObjectOpenHashMap<>();
            String name = "";
            for (LegacyFile legacy : list) {
                String detail;
                boolean death;
                if (legacy.kind() == Kind.SNAPSHOT) {
                    Snapshot snapshot = readSnapshot(store, legacy);
                    if (snapshot == null) {
                        skipped++;
                        continue;
                    }
                    inventory = snapshot.inventory();
                    ender = snapshot.ender();
                    name = snapshot.name().isEmpty() ? name : snapshot.name();
                    curioSlots += snapshot.skippedCurios();
                    detail = "2.0.x " + (snapshot.reason().isEmpty() ? "backup" : snapshot.reason());
                    death = snapshot.reason().toLowerCase(Locale.ROOT).contains("death");
                } else {
                    Int2ObjectMap<ItemStack> items = readJson(legacy);
                    if (items == null) {
                        skipped++;
                        continue;
                    }
                    boolean enderFile = legacy.kind() == Kind.ENDER_CHEST;
                    if (enderFile) {
                        ender = items;
                    } else {
                        inventory = items;
                    }
                    detail = "1.2.x " + (enderFile ? "enderchest " : "inventory ") + (legacy.type().isEmpty() ? "auto" : legacy.type());
                    death = legacy.type().equals("death");
                }
                Int2ObjectMap<ItemStack> state = new Int2ObjectOpenHashMap<>(inventory);
                ender.int2ObjectEntrySet().forEach(e -> state.put(Slots.ENDER_START + e.getIntKey(), e.getValue()));
                Trigger trigger = death ? Trigger.DEATH : Trigger.IMPORT;
                store.write(new Capture(uuid, name, legacy.time(), trigger, EntryContext.NONE.withDetail(detail), true, true, state));
                files++;
            }
            players++;
        }
        if (curioSlots > 0) {
            InventoryBackups.LOGGER.warn("Skipped {} Curios slots of 2.0.x backups that could not be read", curioSlots);
        }
        try {
            Files.createDirectories(store.root());
            Files.writeString(marker, "Imported " + files + " backups of " + players + " players from " + jsonRoot.toAbsolutePath()
                    + " and " + snapshotRoot.toAbsolutePath() + " at " + LocalDateTime.now() + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not write {}; the old backups are left in place", marker, e);
            return new Result(players, files, skipped, curioSlots);
        }
        if (json) {
            finish(jsonRoot, "-1.2.x", action);
        }
        if (snapshots) {
            finish(snapshotRoot, "-2.0.x", action);
        }
        return new Result(players, files, skipped, curioSlots);
    }

    private static int collect(Path kindDir, Kind kind, Map<UUID, List<LegacyFile>> byPlayer) {
        if (!Files.isDirectory(kindDir)) {
            return 0;
        }
        int skipped = 0;
        try (Stream<Path> dirs = Files.list(kindDir)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                UUID uuid = uuid(dir);
                if (uuid == null) {
                    continue;
                }
                try (Stream<Path> files = Files.list(dir)) {
                    for (Path file : files.toList()) {
                        String name = file.getFileName().toString();
                        if (!name.endsWith(".json") || name.length() < NAME_LENGTH + 5) {
                            continue;
                        }
                        try {
                            LocalDateTime time = LocalDateTime.parse(name.substring(0, NAME_LENGTH), NAME);
                            String base = name.substring(0, name.length() - 5);
                            String type = base.length() > NAME_LENGTH + 1 ? base.substring(NAME_LENGTH + 1) : "";
                            long millis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                            byPlayer.computeIfAbsent(uuid, u -> new ArrayList<>()).add(new LegacyFile(millis, kind, type, file));
                        } catch (DateTimeParseException e) {
                            skipped++;
                        }
                    }
                }
            }
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not list old backups in {}", kindDir, e);
        }
        return skipped;
    }

    private static int collectSnapshots(Path root, Map<UUID, List<LegacyFile>> byPlayer) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int skipped = 0;
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                UUID uuid = uuid(dir);
                if (uuid == null) {
                    continue;
                }
                try (Stream<Path> files = Files.list(dir)) {
                    for (Path file : files.toList()) {
                        String name = file.getFileName().toString();
                        if (!name.startsWith(SNAPSHOT_PREFIX) || !name.endsWith(SNAPSHOT_SUFFIX)) {
                            continue;
                        }
                        try {
                            long millis = Long.parseLong(name.substring(SNAPSHOT_PREFIX.length(), name.length() - SNAPSHOT_SUFFIX.length()));
                            byPlayer.computeIfAbsent(uuid, u -> new ArrayList<>()).add(new LegacyFile(millis, Kind.SNAPSHOT, "", file));
                        } catch (NumberFormatException e) {
                            skipped++;
                        }
                    }
                }
            }
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not list old backups in {}", root, e);
        }
        return skipped;
    }

    @Nullable
    private static UUID uuid(Path dir) {
        try {
            return UUID.fromString(dir.getFileName().toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Slots of one 1.2.x file; null when the file cannot be read at all. */
    @Nullable
    private static Int2ObjectMap<ItemStack> readJson(LegacyFile legacy) {
        try (Reader reader = Files.newBufferedReader(legacy.file(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            Int2ObjectMap<ItemStack> items = new Int2ObjectOpenHashMap<>();
            if (!root.isJsonObject() || !root.getAsJsonObject().has("data")) {
                return items;
            }
            JsonArray data = root.getAsJsonObject().getAsJsonArray("data");
            for (JsonElement element : data) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject item = element.getAsJsonObject();
                if (!item.has("index") || !item.has("nbt")) {
                    continue;
                }
                int index = item.get("index").getAsInt();
                try {
                    ItemStack stack = ItemStack.of(TagParser.parseTag(item.get("nbt").getAsString()));
                    if (!stack.isEmpty()) {
                        items.put(index, stack);
                    }
                } catch (CommandSyntaxException | RuntimeException e) {
                    InventoryBackups.LOGGER.warn("Skipping an unreadable item in {}: {}", legacy.file(), e.getMessage());
                }
            }
            return items;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            InventoryBackups.LOGGER.warn("Skipping unreadable old backup {}: {}", legacy.file(), e.getMessage());
            return null;
        }
    }

    /** One 2.0.x snapshot; null when the file cannot be read at all. */
    @Nullable
    private static Snapshot readSnapshot(BackupStore store, LegacyFile legacy) {
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(legacy.file().toFile());
        } catch (IOException | RuntimeException e) {
            InventoryBackups.LOGGER.warn("Skipping unreadable old backup {}: {}", legacy.file(), e.getMessage());
            return null;
        }
        Int2ObjectMap<ItemStack> inventory = new Int2ObjectOpenHashMap<>();
        for (Tag tag : root.getList("Inventory", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) tag;
            int raw = item.getByte("Slot") & 255;
            int slot = raw == FILE_BACKPACK ? backpackSlot(store) : inventorySlot(raw);
            ItemStack stack = stack(item, legacy);
            if (slot != Integer.MIN_VALUE && !stack.isEmpty()) {
                inventory.put(slot, stack);
            }
        }
        Int2ObjectMap<ItemStack> ender = new Int2ObjectOpenHashMap<>();
        for (Tag tag : root.getList("EnderChest", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) tag;
            int slot = item.getByte("Slot") & 255;
            ItemStack stack = stack(item, legacy);
            if (slot < Slots.ENDER_SIZE && !stack.isEmpty()) {
                ender.put(slot, stack);
            }
        }
        int skippedCurios = curios(store, root.getCompound("ForgeCaps").getCompound(CURIOS_CAPABILITY), inventory, legacy);
        return new Snapshot(root.getString("name"), root.getString("reason"), inventory, ender, skippedCurios);
    }

    /** The stored slot of a slot in a player file's inventory list, {@link Integer#MIN_VALUE} for unknown ones. */
    private static int inventorySlot(int slot) {
        if (slot < Slots.MAIN_SIZE) {
            return slot;
        }
        if (slot >= FILE_ARMOR_START && slot < FILE_ARMOR_START + Slots.ARMOR_SIZE) {
            return Slots.ARMOR_START + slot - FILE_ARMOR_START;
        }
        return slot == FILE_OFFHAND ? Slots.OFFHAND : Integer.MIN_VALUE;
    }

    /** The mod slot of the Backpacked backpack, {@link Integer#MIN_VALUE} when every mod slot number is taken. */
    private static int backpackSlot(BackupStore store) {
        int number = store.slotKeys().number(BACKPACK_KEY);
        return number < 0 ? Integer.MIN_VALUE : number;
    }

    /**
     * Adds the Curios slots of a snapshot as mod slots {@code curios:<type>/<index>} and {@code curios:cosmetic/<type>/<index>},
     * the keys the live Curios slots have.
     *
     * @param curios the saved Curios capability: a list {@code Curios} of slot types, each with its {@code Identifier} and a
     *               {@code StacksHandler} whose {@code Stacks} and {@code Cosmetics} hold the items of an item handler
     * @return the number of slots that could not be read
     */
    private static int curios(BackupStore store, CompoundTag curios, Int2ObjectMap<ItemStack> into, LegacyFile legacy) {
        int skipped = 0;
        for (Tag tag : curios.getList("Curios", Tag.TAG_COMPOUND)) {
            CompoundTag type = (CompoundTag) tag;
            String id = type.getString("Identifier");
            CompoundTag handler = type.getCompound("StacksHandler");
            if (id.isEmpty()) {
                skipped += handler.getCompound("Stacks").getList("Items", Tag.TAG_COMPOUND).size();
                continue;
            }
            skipped += curioStacks(store, handler.getCompound("Stacks"), "curios:" + id, into, legacy);
            skipped += curioStacks(store, handler.getCompound("Cosmetics"), "curios:cosmetic/" + id, into, legacy);
        }
        return skipped;
    }

    private static int curioStacks(BackupStore store, CompoundTag stacks, String prefix, Int2ObjectMap<ItemStack> into,
                                   LegacyFile legacy) {
        int skipped = 0;
        for (Tag tag : stacks.getList("Items", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) tag;
            ItemStack stack = stack(item, legacy);
            if (stack.isEmpty()) {
                continue;
            }
            int number = item.contains("Slot", Tag.TAG_ANY_NUMERIC) ? store.slotKeys().number(prefix + "/" + item.getInt("Slot")) : -1;
            if (number < 0) {
                skipped++;
            } else {
                into.put(number, stack);
            }
        }
        return skipped;
    }

    private static ItemStack stack(CompoundTag tag, LegacyFile legacy) {
        try {
            return ItemStack.of(tag);
        } catch (RuntimeException e) {
            InventoryBackups.LOGGER.warn("Skipping an unreadable item in {}: {}", legacy.file(), e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    private static void finish(Path legacyRoot, String suffix, Config.LegacyAction action) {
        try {
            switch (action) {
                case KEEP -> {
                }
                case ARCHIVE -> {
                    Path target = legacyRoot.resolveSibling(legacyRoot.getFileName() + suffix);
                    for (int i = 2; Files.exists(target); i++) {
                        target = legacyRoot.resolveSibling(legacyRoot.getFileName() + suffix + "-" + i);
                    }
                    Files.move(legacyRoot, target);
                    InventoryBackups.LOGGER.info("Moved the imported old backups to {}", target);
                }
                case DELETE -> {
                    try (Stream<Path> files = Files.walk(legacyRoot)) {
                        for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                            Files.deleteIfExists(file);
                        }
                    }
                    InventoryBackups.LOGGER.info("Deleted the imported old backups in {}", legacyRoot);
                }
            }
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not {} the old backups in {}", action.name().toLowerCase(Locale.ROOT), legacyRoot, e);
        }
    }
}
