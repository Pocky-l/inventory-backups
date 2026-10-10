package com.pockyl.inventorybackups.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.DayFiles;
import com.pockyl.inventorybackups.storage.Entry;
import com.pockyl.inventorybackups.storage.Retention;
import com.pockyl.inventorybackups.storage.StateView;
import com.pockyl.inventorybackups.storage.Trigger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.pockyl.inventorybackups.gametest.TestStores.capture;
import static com.pockyl.inventorybackups.gametest.TestStores.slots;

/**
 * Thinning by age, the newest-entry guarantee, disk budgets and cleanup of empty folders.
 */
@GameTestHolder(InventoryBackups.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RetentionTests {
    private static final long MINUTE = 60_000L;
    private static final long DAY = 24 * 60 * MINUTE;
    private static final long NOW = DayFiles.startOf(LocalDate.of(2026, 6, 1)) + 12 * 60 * MINUTE;
    private static final Retention.Policy POLICY = Retention.Policy.ofDays(24, 7, 30, 90, 0, 0, 4);

    private RetentionTests() {
    }

    @GameTest(template = "empty")
    public static void oldEntriesAreThinned(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            UUID player = UUID.randomUUID();
            long expired = dayStart(NOW - 100 * DAY);
            long daily = dayStart(NOW - 20 * DAY);
            long hourly = dayStart(NOW - 3 * DAY);
            fillDay(store, player, expired, true);
            fillDay(store, player, daily, true);
            fillDay(store, player, hourly, true);
            for (int i = 0; i < 10; i++) {
                store.submit(capture(player, NOW - 60 * MINUTE + i * MINUTE, Trigger.CHANGE, false,
                        slots(2, new ItemStack(Items.COAL, i + 1))));
            }
            store.awaitIdle();
            StateView hourlyBefore = store.call(() -> store.stateAt(player, hourly + 13 * 60 * MINUTE + 45 * MINUTE)).join().orElseThrow();

            Retention.Result result = store.call(() -> Retention.run(store, POLICY, NOW)).join();
            helper.assertTrue(result.deleted() == 1, "Expired day not deleted: " + result);
            List<LocalDate> days = store.call(() -> store.days(player)).join();
            helper.assertTrue(days.size() == 3 && days.get(0).equals(DayFiles.day(daily)), "Unexpected days " + days);

            List<Entry> dailyEntries = entriesOf(store, player, daily);
            helper.assertTrue(dailyEntries.size() == 2, "Daily zone keeps the last state and the death, got " + dailyEntries.size());
            helper.assertTrue(dailyEntries.stream().anyMatch(e -> e.trigger() == Trigger.DEATH), "Death not kept");
            helper.assertTrue(dailyEntries.get(0).keyframe(), "A thinned file must start with a keyframe");

            List<Entry> hourlyEntries = entriesOf(store, player, hourly);
            helper.assertTrue(hourlyEntries.size() == 25, "Hourly zone keeps one state per hour and the death, got "
                    + hourlyEntries.size());
            StateView hourlyAfter = store.call(() -> store.stateAt(player, hourly + 13 * 60 * MINUTE + 45 * MINUTE)).join().orElseThrow();
            helper.assertTrue(ItemStack.matches(hourlyAfter.items().get(0), hourlyBefore.items().get(0))
                    && hourlyAfter.items().get(1) != null, "Kept state changed by thinning");
            helper.assertTrue(entriesOf(store, player, NOW).size() == 10, "Recent changes were thinned");

            Retention.Result again = store.call(() -> Retention.run(store, POLICY, NOW)).join();
            helper.assertTrue(again.rewritten() == 0 && again.deleted() == 0, "Second pass did work again: " + again);
            helper.succeed();
        } finally {
            TestStores.delete(store);
        }
    }

    @GameTest(template = "empty")
    public static void newestStateSurvivesInactivity(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            UUID player = UUID.randomUUID();
            long ancient = dayStart(NOW - 400 * DAY);
            fillDay(store, player, ancient, false);
            store.awaitIdle();
            store.call(() -> Retention.run(store, POLICY, NOW)).join();
            List<Entry> left = entriesOf(store, player, ancient);
            helper.assertTrue(left.size() == 1, "Expected the last state only, got " + left.size());
            StateView latest = store.call(() -> store.latest(player)).join().orElseThrow();
            helper.assertTrue(latest.items().get(0).getCount() == 48, "Last state not kept");
            helper.succeed();
        } finally {
            TestStores.delete(store);
        }
    }

    @GameTest(template = "empty")
    public static void budgetsDeleteOldestDays(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            UUID player = UUID.randomUUID();
            for (int day = 5; day >= 1; day--) {
                fillDay(store, player, dayStart(NOW - day * DAY), false);
            }
            store.awaitIdle();
            long newestSize = store.call(() -> size(store.playerDir(player), DayFiles.day(NOW - DAY))).join();
            Retention.Policy tight = Retention.Policy.ofDays(24 * 30, 7, 30, 90, newestSize + 10, 0, 4);
            store.call(() -> Retention.run(store, tight, NOW)).join();
            List<LocalDate> days = store.call(() -> store.days(player)).join();
            helper.assertTrue(days.size() == 1 && days.get(0).equals(DayFiles.day(NOW - DAY)), "Budget kept " + days);

            Retention.Policy total = Retention.Policy.ofDays(24 * 30, 7, 30, 90, 0, 1, 4);
            store.call(() -> Retention.run(store, total, NOW)).join();
            helper.assertTrue(store.call(() -> store.days(player)).join().size() == 1, "Total budget deleted the newest day");
            helper.succeed();
        } finally {
            TestStores.delete(store);
        }
    }

    @GameTest(template = "empty")
    public static void emptyFoldersAreRemoved(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            Path empty = store.playerDir(UUID.randomUUID());
            Files.createDirectories(empty);
            store.call(() -> Retention.run(store, POLICY, NOW)).join();
            helper.assertFalse(Files.exists(empty), "Empty player folder left behind");
            helper.assertFalse(Files.exists(store.playersDir()), "Empty players folder left behind");
            helper.succeed();
        } catch (IOException e) {
            helper.fail("IO error: " + e.getMessage());
        } finally {
            TestStores.delete(store);
        }
    }

    /** 48 changes, one every 30 minutes, and optionally a death at 12:10. */
    private static void fillDay(BackupStore store, UUID player, long start, boolean death) {
        for (int k = 0; k < 48; k++) {
            long time = start + k * 30 * MINUTE;
            store.submit(capture(player, time, Trigger.CHANGE, false, slots(0, new ItemStack(Items.STONE, k + 1))));
            if (death && k == 24) {
                store.submit(capture(player, time + 10 * MINUTE, Trigger.DEATH, false, slots(1, new ItemStack(Items.DIAMOND))));
            }
        }
    }

    private static List<Entry> entriesOf(BackupStore store, UUID player, long anyTimeOfDay) {
        long start = dayStart(anyTimeOfDay);
        return store.call(() -> store.entries(player, start, start + DAY - 1)).join();
    }

    private static long dayStart(long time) {
        return DayFiles.startOf(DayFiles.day(time));
    }

    private static long size(Path dir, LocalDate day) {
        try {
            return Files.size(DayFiles.file(dir, day));
        } catch (IOException e) {
            return 0;
        }
    }
}
