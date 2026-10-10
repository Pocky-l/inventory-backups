package com.pockyl.inventorybackups.storage;

import com.pockyl.inventorybackups.InventoryBackups;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Naming of day files: {@code <yyyy-MM-dd>.ibk}, one per player and UTC day, so daylight saving never splits or merges a
 * file.
 */
public final class DayFiles {
    private DayFiles() {
    }

    public static LocalDate day(long time) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(time), ZoneOffset.UTC);
    }

    public static long startOf(LocalDate day) {
        return day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    public static Path file(Path dir, LocalDate day) {
        return dir.resolve(day + LogFile.EXTENSION);
    }

    /** Days with a file in the folder, oldest first. */
    public static List<LocalDate> days(Path dir) {
        List<LocalDate> days = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return days;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(LogFile.EXTENSION)) {
                    continue;
                }
                try {
                    days.add(LocalDate.parse(name.substring(0, name.length() - LogFile.EXTENSION.length())));
                } catch (DateTimeParseException e) {
                    InventoryBackups.LOGGER.debug("Ignoring unexpected file {}", file);
                }
            }
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not list {}", dir, e);
        }
        days.sort(null);
        return days;
    }

    /** Renames an unreadable file so it is kept for inspection but no longer read or appended to. */
    static void setAside(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()));
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not set aside {}", file, e);
        }
    }

    /** Deletes a folder if it is empty. */
    static void deleteIfEmpty(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            if (entries.findAny().isEmpty()) {
                Files.delete(dir);
            }
        } catch (IOException e) {
            InventoryBackups.LOGGER.debug("Could not remove empty folder {}", dir, e);
        }
    }
}
