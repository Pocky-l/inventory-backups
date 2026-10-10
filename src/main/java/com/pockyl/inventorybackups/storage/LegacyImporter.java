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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * One-time import of the 2.x backups: {@code InventoryLog/inventory|enderchest/<uuid>/<yyyy-MM-dd-HH-mm-ss>[-type].json},
 * each holding the non-empty slots as SNBT. Both kinds of a player are merged into one timeline in time order. A marker
 * file in the storage root makes sure the import runs once; afterwards the old folder is kept, archived or deleted as
 * configured. Runs on the storage thread.
 */
public final class LegacyImporter {
    public static final String MARKER = "legacy-imported.txt";
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final int NAME_LENGTH = 19;

    private LegacyImporter() {
    }

    public record Result(int players, int files, int skippedFiles) {
    }

    private record LegacyFile(long time, boolean ender, String type, Path file) {
    }

    /**
     * Player names are not known here; the player browser fills them in from the server's profile cache.
     *
     * @return null when there was nothing to import or the import already ran
     */
    @Nullable
    public static Result run(BackupStore store, Path legacyRoot, Config.LegacyAction action) {
        Path marker = store.root().resolve(MARKER);
        if (Files.exists(marker) || !Files.isDirectory(legacyRoot)) {
            return null;
        }
        Map<UUID, List<LegacyFile>> byPlayer = new HashMap<>();
        int skipped = 0;
        skipped += collect(legacyRoot.resolve("inventory"), false, byPlayer);
        skipped += collect(legacyRoot.resolve("enderchest"), true, byPlayer);

        int files = 0;
        int players = 0;
        for (Map.Entry<UUID, List<LegacyFile>> player : byPlayer.entrySet()) {
            UUID uuid = player.getKey();
            if (!store.days(uuid).isEmpty()) {
                // Imported entries are older than anything new, and a timeline only grows forward.
                InventoryBackups.LOGGER.warn("Not importing 2.x backups of {}: the player already has 3.0 backups", uuid);
                continue;
            }
            List<LegacyFile> list = player.getValue();
            list.sort(Comparator.comparingLong(LegacyFile::time));
            Int2ObjectMap<ItemStack> inventory = new Int2ObjectOpenHashMap<>();
            Int2ObjectMap<ItemStack> ender = new Int2ObjectOpenHashMap<>();
            for (LegacyFile legacy : list) {
                Int2ObjectMap<ItemStack> items = read(store, legacy);
                if (items == null) {
                    skipped++;
                    continue;
                }
                if (legacy.ender()) {
                    ender = items;
                } else {
                    inventory = items;
                }
                Int2ObjectMap<ItemStack> state = new Int2ObjectOpenHashMap<>(inventory);
                ender.int2ObjectEntrySet().forEach(e -> state.put(Slots.ENDER_START + e.getIntKey(), e.getValue()));
                Trigger trigger = legacy.type().equals("death") ? Trigger.DEATH : Trigger.IMPORT;
                EntryContext context = EntryContext.NONE.withDetail("2.x " + (legacy.ender() ? "enderchest " : "inventory ")
                        + (legacy.type().isEmpty() ? "auto" : legacy.type()));
                store.write(new Capture(uuid, "", legacy.time(), trigger, context, true, true, state));
                files++;
            }
            players++;
        }
        try {
            Files.createDirectories(store.root());
            Files.writeString(marker, "Imported " + files + " backups of " + players + " players from " + legacyRoot.toAbsolutePath()
                    + " at " + LocalDateTime.now() + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not write {}; the 2.x backups are left in place", marker, e);
            return new Result(players, files, skipped);
        }
        finish(legacyRoot, action);
        return new Result(players, files, skipped);
    }

    private static int collect(Path kindDir, boolean ender, Map<UUID, List<LegacyFile>> byPlayer) {
        if (!Files.isDirectory(kindDir)) {
            return 0;
        }
        int skipped = 0;
        try (Stream<Path> dirs = Files.list(kindDir)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(dir.getFileName().toString());
                } catch (IllegalArgumentException e) {
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
                            byPlayer.computeIfAbsent(uuid, u -> new ArrayList<>()).add(new LegacyFile(millis, ender, type, file));
                        } catch (DateTimeParseException e) {
                            skipped++;
                        }
                    }
                }
            }
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not list 2.x backups in {}", kindDir, e);
        }
        return skipped;
    }

    /** Slots of one 2.x file; null when the file cannot be read at all. */
    @Nullable
    private static Int2ObjectMap<ItemStack> read(BackupStore store, LegacyFile legacy) {
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
                    CompoundTag tag = TagParser.parseTag(item.get("nbt").getAsString());
                    ItemStack stack = ItemStack.parseOptional(store.registries(), tag);
                    if (!stack.isEmpty()) {
                        items.put(index, stack);
                    }
                } catch (CommandSyntaxException | RuntimeException e) {
                    InventoryBackups.LOGGER.warn("Skipping an unreadable item in {}: {}", legacy.file(), e.getMessage());
                }
            }
            return items;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            InventoryBackups.LOGGER.warn("Skipping unreadable 2.x backup {}: {}", legacy.file(), e.getMessage());
            return null;
        }
    }

    private static void finish(Path legacyRoot, Config.LegacyAction action) {
        try {
            switch (action) {
                case KEEP -> {
                }
                case ARCHIVE -> {
                    Path target = legacyRoot.resolveSibling(legacyRoot.getFileName() + "-2.x");
                    for (int i = 2; Files.exists(target); i++) {
                        target = legacyRoot.resolveSibling(legacyRoot.getFileName() + "-2.x-" + i);
                    }
                    Files.move(legacyRoot, target);
                    InventoryBackups.LOGGER.info("Moved the imported 2.x backups to {}", target);
                }
                case DELETE -> {
                    try (Stream<Path> files = Files.walk(legacyRoot)) {
                        for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                            Files.deleteIfExists(file);
                        }
                    }
                    InventoryBackups.LOGGER.info("Deleted the imported 2.x backups in {}", legacyRoot);
                }
            }
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not {} the 2.x backups in {}", action.name().toLowerCase(), legacyRoot, e);
        }
    }
}
