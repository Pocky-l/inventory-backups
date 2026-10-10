package com.pockyl.inventorybackups.gametest;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.Capture;
import com.pockyl.inventorybackups.storage.EntryContext;
import com.pockyl.inventorybackups.storage.Trigger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Helpers for tests that need a storage of their own in a temporary folder, so they never touch the world's backups.
 */
final class TestStores {
    private TestStores() {
    }

    static BackupStore create(GameTestHelper helper) {
        try {
            Path root = Files.createTempDirectory("inventorybackups-test");
            return new BackupStore(root, helper.getLevel().registryAccess(), () -> 4);
        } catch (IOException e) {
            throw new GameTestAssertException("Could not create a temporary folder: " + e.getMessage());
        }
    }

    static void delete(BackupStore store) {
        store.close();
        try (Stream<Path> files = Files.walk(store.root())) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            InventoryBackups.LOGGER.warn("Could not delete test storage {}", store.root(), e);
        }
    }

    static Int2ObjectMap<ItemStack> slots(Object... slotsAndStacks) {
        Int2ObjectMap<ItemStack> map = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < slotsAndStacks.length; i += 2) {
            map.put((int) slotsAndStacks[i], ((ItemStack) slotsAndStacks[i + 1]).copy());
        }
        return map;
    }

    static Capture capture(UUID player, long time, Trigger trigger, boolean full, Int2ObjectMap<ItemStack> changes) {
        return new Capture(player, "tester", time, trigger, EntryContext.NONE, full, true, changes);
    }
}
