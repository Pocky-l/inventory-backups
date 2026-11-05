package com.pockyl.inventorybackups.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.Slots;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.Entry;
import com.pockyl.inventorybackups.storage.LegacyImporter;
import com.pockyl.inventorybackups.storage.StateView;
import com.pockyl.inventorybackups.storage.Trigger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Import of 2.x JSON backups and compatibility with 2.x config files.
 */
@GameTestHolder(InventoryBackups.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MigrationTests {
    private MigrationTests() {
    }

    @GameTest(template = "empty")
    public static void legacyBackupsAreImportedOnce(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            UUID player = UUID.randomUUID();
            Path legacy = store.root().resolve("InventoryLog");
            write(legacy.resolve("inventory").resolve(player.toString()).resolve("2025-01-15-18-30-45-join.json"),
                    "{\"data\":[{\"index\":0,\"nbt\":\"{count:5,id:\\\"minecraft:bread\\\"}\"},"
                            + "{\"index\":103,\"nbt\":\"{count:1,id:\\\"minecraft:iron_helmet\\\"}\"}]}");
            write(legacy.resolve("enderchest").resolve(player.toString()).resolve("2025-01-15-18-31-00.json"),
                    "{\"data\":[{\"index\":26,\"nbt\":\"{count:12,id:\\\"minecraft:diamond\\\"}\"}]}");
            write(legacy.resolve("inventory").resolve(player.toString()).resolve("2025-01-15-18-40-00-death.json"),
                    "{\"data\":[{\"index\":0,\"nbt\":\"{count:2,id:\\\"minecraft:bread\\\"}\"},{\"index\":7,\"nbt\":\"broken{\"}]}");
            write(legacy.resolve("inventory").resolve(player.toString()).resolve("2025-01-15-19-00-00.json"), "not json");

            LegacyImporter.Result result = store.call(() -> LegacyImporter.run(store, legacy, Config.LegacyAction.ARCHIVE)).join();
            helper.assertTrue(result != null && result.files() == 3 && result.skippedFiles() == 1, "Unexpected import result " + result);
            List<Entry> entries = store.call(() -> store.entries(player, 0, Long.MAX_VALUE)).join();
            helper.assertTrue(entries.size() == 3, "Expected 3 entries, got " + entries.size());
            helper.assertTrue(entries.get(0).trigger() == Trigger.IMPORT && entries.get(2).trigger() == Trigger.DEATH,
                    "Triggers not mapped");

            StateView afterEnder = store.call(() -> store.stateAt(player, entries.get(1).time())).join().orElseThrow();
            helper.assertTrue(afterEnder.items().get(0).getCount() == 5, "Inventory lost when the ender chest was imported");
            helper.assertTrue(afterEnder.items().get(Slots.ENDER_START + 26).is(Items.DIAMOND), "Ender chest not merged");
            StateView death = store.call(() -> store.latest(player)).join().orElseThrow();
            helper.assertTrue(death.items().get(0).getCount() == 2 && !death.items().containsKey(103), "Death state wrong");
            helper.assertTrue(death.items().get(Slots.ENDER_START + 26) != null, "Ender chest not carried forward");

            helper.assertFalse(Files.exists(legacy), "2.x folder not archived");
            helper.assertTrue(Files.isDirectory(store.root().resolve("InventoryLog-2.x")), "Archive missing");
            helper.assertTrue(store.call(() -> LegacyImporter.run(store, legacy, Config.LegacyAction.KEEP)).join() == null,
                    "Import ran twice");
            helper.succeed();
        } catch (IOException e) {
            helper.fail("IO error: " + e.getMessage());
        } finally {
            TestStores.delete(store);
        }
    }

    /** The keys of 2.x config files stay part of the spec, so old files load without being reset. */
    @GameTest(template = "empty")
    public static void legacyConfigKeysStayValid(GameTestHelper helper) {
        for (String key : List.of("general.tickSaveEnabled", "general.preservationPeriod", "general.deadSaveEnabled",
                "general.joinSaveEnabled", "general.quitSaveEnabled", "general.retentionDays", "enderchest.enderChestEnabled",
                "enderchest.enderChestTickSaveEnabled", "enderchest.enderChestDeadSaveEnabled", "enderchest.enderChestJoinSaveEnabled",
                "enderchest.enderChestQuitSaveEnabled", "enderchest.enderChestOpenSaveEnabled")) {
            helper.assertTrue(Config.SPEC.getSpec().contains(key), "2.x config key missing: " + key);
        }
        helper.succeed();
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
