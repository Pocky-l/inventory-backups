package com.pockyl.inventorybackups.backup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.InventoryBackups;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Backup files on disk: {@code InventoryLog/<kind>/<player uuid>/<yyyy-MM-dd-HH-mm-ss>[-<suffix>].json}, relative to
 * the server directory. The layout is the one of the 2.0 releases, so old backups stay readable.
 */
public final class BackupStorage {
    private static final Path ROOT = Path.of("InventoryLog");
    private static final String EXTENSION = ".json";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    /** Backup names come from command arguments, so they must never be able to point outside the backup folder. */
    private static final Pattern NAME = Pattern.compile("[0-9A-Za-z_-]{1,64}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BackupStorage() {
    }

    public static boolean isValidName(String name) {
        return NAME.matcher(name).matches();
    }

    /** Writes a backup named after the current time and returns its name. */
    public static Optional<String> save(BackupKind kind, UUID player, BackupData backup, @Nullable String suffix) {
        String name = LocalDateTime.now().format(TIMESTAMP) + (suffix == null || suffix.isEmpty() ? "" : "-" + suffix);
        Path file = directory(kind, player).resolve(name + EXTENSION);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(backup), StandardCharsets.UTF_8);
            return Optional.of(name);
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not save {} backup {} of player {}", kind.id(), name, player, e);
            return Optional.empty();
        }
    }

    /** Reads a backup; empty when the name is invalid, the file is missing or it cannot be parsed. */
    public static Optional<BackupData> load(BackupKind kind, UUID player, String name) {
        if (!isValidName(name)) {
            return Optional.empty();
        }
        Path file = directory(kind, player).resolve(name + EXTENSION);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return Optional.ofNullable(GSON.fromJson(reader, BackupData.class));
        } catch (IOException | JsonParseException e) {
            InventoryBackups.LOGGER.error("Could not read {} backup {} of player {}", kind.id(), name, player, e);
            return Optional.empty();
        }
    }

    /** Names of all backups of a player, newest first. */
    public static List<String> list(BackupKind kind, UUID player) {
        Path dir = directory(kind, player);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(EXTENSION))
                    .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                    .sorted(Comparator.reverseOrder())
                    .toList();
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not list {} backups of player {}", kind.id(), player, e);
            return List.of();
        }
    }

    /** Deletes the backups of every player created before the cutoff and returns how many were deleted. */
    public static int deleteOlderThan(BackupKind kind, Instant cutoff) {
        Path kindDir = ROOT.resolve(kind.id());
        if (!Files.isDirectory(kindDir)) {
            return 0;
        }
        int deleted = 0;
        List<Path> playerDirs = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(kindDir)) {
            dirs.filter(Files::isDirectory).forEach(playerDirs::add);
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not list backup folder {}", kindDir, e);
            return 0;
        }
        for (Path playerDir : playerDirs) {
            try (Stream<Path> files = Files.list(playerDir)) {
                for (Path file : files.filter(f -> f.getFileName().toString().endsWith(EXTENSION)).toList()) {
                    Instant created = Files.readAttributes(file, BasicFileAttributes.class).creationTime().toInstant();
                    if (created.isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                        deleted++;
                    }
                }
            } catch (IOException e) {
                InventoryBackups.LOGGER.warn("Could not clean up backups in {}", playerDir, e);
            }
            deleteIfEmpty(playerDir);
        }
        return deleted;
    }

    public static Path directory(BackupKind kind, UUID player) {
        return ROOT.resolve(kind.id()).resolve(player.toString());
    }

    private static void deleteIfEmpty(Path dir) {
        try (Stream<Path> entries = Files.list(dir)) {
            if (entries.findAny().isEmpty()) {
                Files.delete(dir);
            }
        } catch (IOException e) {
            InventoryBackups.LOGGER.debug("Could not remove empty backup folder {}", dir, e);
        }
    }
}
