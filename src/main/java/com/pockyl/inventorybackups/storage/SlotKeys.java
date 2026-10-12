package com.pockyl.inventorybackups.storage;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.Slots;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Stable numbers for the slots of other mods. A mod slot is named by a key such as {@code curios:ring/1}; the first
 * time a key holds an item it gets the next free number from {@link Slots#MOD_START} on, and keeps it for the world.
 * So a stored number means the same slot even after mods add or remove slot types. Saved to {@code slots.txt}, one
 * {@code <number> <key>} line per slot, appended when a number is handed out. Used from the main and the storage thread.
 */
public final class SlotKeys {
    private final Path file;
    private final Object2IntMap<String> numbers = new Object2IntOpenHashMap<>();
    private final Int2ObjectMap<String> keys = new Int2ObjectOpenHashMap<>();
    private int next = Slots.MOD_START;

    SlotKeys(Path file) {
        this.file = file;
        numbers.defaultReturnValue(-1);
        load();
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int space = line.indexOf(' ');
                if (space <= 0) {
                    continue;
                }
                try {
                    int number = Integer.parseInt(line.substring(0, space));
                    String key = line.substring(space + 1).trim();
                    if (Slots.isModSlot(number) && !key.isEmpty() && !numbers.containsKey(key) && !keys.containsKey(number)) {
                        numbers.put(key, number);
                        keys.put(number, key);
                        next = Math.max(next, number + 1);
                    }
                } catch (NumberFormatException ignored) {
                    // A damaged line only loses that slot's name; its number is never handed out again below.
                }
            }
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not read {}", file, e);
        }
    }

    /** The number of a slot, handing out a new one for a new key; -1 when every number is taken. */
    public synchronized int number(String key) {
        int number = numbers.getInt(key);
        if (number >= 0) {
            return number;
        }
        if (next >= Slots.MOD_END) {
            return -1;
        }
        number = next++;
        numbers.put(key, number);
        keys.put(number, key);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, number + " " + key + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not save the number of mod slot {} to {}", key, file, e);
        }
        return number;
    }

    /** The number of a slot that already has one, or -1. */
    public synchronized int find(String key) {
        return numbers.getInt(key);
    }

    /** The key of a numbered slot, or null. */
    @Nullable
    public synchronized String key(int number) {
        return keys.get(number);
    }
}
