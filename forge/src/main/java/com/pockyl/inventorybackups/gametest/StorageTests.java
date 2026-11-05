package com.pockyl.inventorybackups.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.Slots;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.DayFiles;
import com.pockyl.inventorybackups.storage.DayLog;
import com.pockyl.inventorybackups.storage.Entry;
import com.pockyl.inventorybackups.storage.StateView;
import com.pockyl.inventorybackups.storage.Trigger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

import static com.pockyl.inventorybackups.gametest.TestStores.capture;
import static com.pockyl.inventorybackups.gametest.TestStores.slots;

/**
 * The binary delta storage: round trips, deltas, keyframes, the item pool and crash recovery.
 */
@GameTestHolder(InventoryBackups.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StorageTests {
    private static final long T0 = 1_760_000_000_000L;

    private StorageTests() {
    }

    @GameTest(template = "empty")
    public static void deltasRebuildEveryState(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            UUID player = UUID.randomUUID();
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.setHoverName(Component.literal("Backup Blade"));
            sword.setDamageValue(42);
            store.submit(capture(player, T0, Trigger.JOIN, true, slots(5, sword, 35, new ItemStack(Items.COBBLESTONE, 37),
                    Slots.OFFHAND, new ItemStack(Items.SHIELD), Slots.ENDER_START + 26, new ItemStack(Items.DIAMOND, 12))));
            store.submit(capture(player, T0 + 1000, Trigger.CHANGE, false, slots(35, new ItemStack(Items.COBBLESTONE, 40),
                    0, new ItemStack(Items.APPLE, 3))));
            store.submit(capture(player, T0 + 2000, Trigger.CHANGE, false, slots(5, ItemStack.EMPTY)));
            store.awaitIdle();

            StateView first = store.call(() -> store.stateAt(player, T0 + 500)).join().orElseThrow();
            helper.assertTrue(ItemStack.matches(first.items().get(5), sword), "Sword not stored with its data");
            helper.assertTrue(first.items().get(Slots.ENDER_START + 26).getCount() == 12, "Ender chest slot lost");
            helper.assertTrue(first.previous() == null, "Oldest entry has a previous entry");

            StateView second = store.call(() -> store.stateAt(player, T0 + 1000)).join().orElseThrow();
            helper.assertTrue(second.items().get(35).getCount() == 40, "Count change lost");
            helper.assertTrue(second.items().get(0).is(Items.APPLE), "Added item lost");
            helper.assertTrue(ItemStack.matches(second.items().get(5), sword), "Unchanged slot lost in a delta");
            helper.assertTrue(second.entry().changed() == 2 && !second.entry().keyframe(), "Delta entry is not minimal");
            helper.assertTrue(second.previousItems().get(35).getCount() == 37, "Previous state wrong");

            StateView third = store.call(() -> store.latest(player)).join().orElseThrow();
            helper.assertTrue(!third.items().containsKey(5), "Removed item still present");
            helper.assertTrue(third.items().size() == 4, "Unexpected slots: " + third.items().keySet());
            helper.succeed();
        } finally {
            TestStores.delete(store);
        }
    }

    @GameTest(template = "empty")
    public static void playersAreFoundByTheirName(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            UUID player = UUID.randomUUID();
            store.submit(capture(player, T0, Trigger.JOIN, true, slots(0, new ItemStack(Items.BREAD, 5))));
            store.awaitIdle();
            helper.assertTrue(store.playerByName("TESTER").map(info -> info.uuid().equals(player)).orElse(false),
                    "an offline player with backups is found by name, in any case");
            helper.assertTrue(store.playerByName("nobody").isEmpty(), "unknown names are not found");
            helper.succeed();
        } finally {
            TestStores.delete(store);
        }
    }

    @GameTest(template = "empty")
    public static void unchangedStatesAreNotDuplicated(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            UUID player = UUID.randomUUID();
            store.submit(capture(player, T0, Trigger.JOIN, true, slots(0, new ItemStack(Items.BREAD, 5))));
            store.submit(capture(player, T0 + 10, Trigger.CHANGE, false, slots(0, new ItemStack(Items.BREAD, 5))));
            store.submit(capture(player, T0 + 20, Trigger.CHANGE, true, slots(0, new ItemStack(Items.BREAD, 5))));
            store.submit(capture(player, T0 + 30, Trigger.QUIT, false, slots()));
            store.awaitIdle();
            List<Entry> entries = store.call(() -> store.entries(player, 0, Long.MAX_VALUE)).join();
            helper.assertTrue(entries.size() == 2, "Expected join and quit only, got " + entries.size());
            helper.assertTrue(entries.get(1).trigger() == Trigger.QUIT && entries.get(1).changed() == 0, "Quit marker wrong");
            helper.assertTrue(store.stats().skipped() == 2, "Duplicates not counted as skipped");
            helper.succeed();
        } finally {
            TestStores.delete(store);
        }
    }

    @GameTest(template = "empty")
    public static void identicalStacksAreStoredOnce(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            UUID player = UUID.randomUUID();
            Object[] many = new Object[36 * 2];
            for (int i = 0; i < 36; i++) {
                many[i * 2] = i;
                many[i * 2 + 1] = new ItemStack(Items.TORCH, 1 + i);
            }
            store.submit(capture(player, T0, Trigger.JOIN, true, slots(many)));
            store.awaitIdle();
            DayLog log = store.call(() -> store.day(player, DayFiles.day(T0))).join().orElseThrow();
            helper.assertTrue(log.poolSize() == 1, "Torch stacks stored " + log.poolSize() + " times");
            helper.succeed();
        } finally {
            TestStores.delete(store);
        }
    }

    @GameTest(template = "empty")
    public static void keyframesAndDayFiles(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            UUID player = UUID.randomUUID();
            store.submit(capture(player, T0, Trigger.JOIN, true, slots(0, new ItemStack(Items.STONE, 1))));
            for (int i = 1; i <= 6; i++) {
                store.submit(capture(player, T0 + i, Trigger.CHANGE, false, slots(0, new ItemStack(Items.STONE, 1 + i))));
            }
            long nextDay = T0 + 86_400_000L;
            store.submit(capture(player, nextDay, Trigger.CHANGE, false, slots(1, new ItemStack(Items.DIRT))));
            store.awaitIdle();

            List<Entry> entries = store.call(() -> store.entries(player, 0, Long.MAX_VALUE)).join();
            helper.assertTrue(entries.size() == 8, "Expected 8 entries, got " + entries.size());
            helper.assertTrue(entries.get(5).keyframe(), "Keyframe interval not applied");
            helper.assertTrue(entries.get(7).keyframe(), "A new day file must start with a keyframe");
            helper.assertTrue(store.call(() -> store.days(player)).join().size() == 2, "Expected one file per day");
            StateView latest = store.call(() -> store.latest(player)).join().orElseThrow();
            helper.assertTrue(latest.items().get(0).getCount() == 7 && latest.items().get(1).is(Items.DIRT), "State lost across days");
            helper.assertTrue(latest.previous() != null && latest.previousItems().get(0).getCount() == 7, "Previous day not used for diff");
            helper.succeed();
        } finally {
            TestStores.delete(store);
        }
    }

    @GameTest(template = "empty")
    public static void tornFramesAreRecovered(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        BackupStore reopened = null;
        try {
            UUID player = UUID.randomUUID();
            store.submit(capture(player, T0, Trigger.JOIN, true, slots(0, new ItemStack(Items.EMERALD, 3))));
            store.submit(capture(player, T0 + 5, Trigger.CHANGE, false, slots(1, new ItemStack(Items.GOLD_INGOT, 2))));
            store.awaitIdle();
            Path file = DayFiles.file(store.playerDir(player), DayFiles.day(T0));
            // A crash in the middle of a write leaves a partial frame behind.
            Files.write(file, new byte[] {0, 0, 1, 0, 42, 42, 42}, StandardOpenOption.APPEND);

            reopened = new BackupStore(store.root(), helper.getLevel().registryAccess(), () -> 4);
            BackupStore second = reopened;
            second.submit(capture(player, T0 + 10, Trigger.CHANGE, false, slots(2, new ItemStack(Items.IRON_INGOT, 4))));
            second.awaitIdle();
            StateView latest = second.call(() -> second.latest(player)).join().orElseThrow();
            helper.assertTrue(latest.items().size() == 3, "Entries lost after a torn write: " + latest.items().keySet());
            helper.assertTrue(second.call(() -> second.entries(player, 0, Long.MAX_VALUE)).join().size() == 3, "Torn frame not cut off");
            helper.succeed();
        } catch (IOException e) {
            helper.fail("IO error: " + e.getMessage());
        } finally {
            if (reopened != null) {
                reopened.close();
            }
            TestStores.delete(store);
        }
    }

    @GameTest(template = "empty")
    public static void entryTimesAreUnique(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            UUID player = UUID.randomUUID();
            store.submit(capture(player, T0, Trigger.JOIN, true, slots(0, new ItemStack(Items.STICK))));
            store.submit(capture(player, T0, Trigger.DEATH, false, slots(0, ItemStack.EMPTY)));
            store.awaitIdle();
            List<Entry> entries = store.call(() -> store.entries(player, 0, Long.MAX_VALUE)).join();
            helper.assertTrue(entries.size() == 2 && entries.get(1).time() == T0 + 1, "Entry times collide");
            helper.succeed();
        } finally {
            TestStores.delete(store);
        }
    }
}
