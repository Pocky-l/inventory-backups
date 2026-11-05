package com.pockyl.inventorybackups.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.Restorer;
import com.pockyl.inventorybackups.backup.Slots;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.Entry;
import com.pockyl.inventorybackups.storage.StateView;
import com.pockyl.inventorybackups.storage.Trigger;
import com.pockyl.inventorybackups.ui.DeathAlerts;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The live change tracker: joins, coalescing, deaths, in-place changes, the ender chest and restores.
 */
@GameTestHolder(InventoryBackups.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CaptureTests {
    private static final int TIMEOUT = 400;

    private CaptureTests() {
    }

    /** Moving items around for a while makes one entry once the inventory is still, not one per move. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void burstsAreCoalesced(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper);
        UUID uuid = player.getUUID();
        int[] step = {0};
        helper.startSequence()
                .thenWaitUntil(() -> assertEntries(uuid, 1, Trigger.JOIN))
                .thenExecuteFor(20, () -> {
                    player.getInventory().setItem(step[0] % 9, new ItemStack(Items.OAK_LOG, 1 + step[0]));
                    step[0]++;
                    TestPlayers.syncSlots(player);
                })
                .thenWaitUntil(() -> assertEntries(uuid, 2, Trigger.CHANGE))
                .thenIdle(20)
                .thenExecute(() -> {
                    assertEntries(uuid, 2, Trigger.CHANGE);
                    StateView latest = latest(uuid);
                    helper.assertTrue(latest.items().get((step[0] - 1) % 9).getCount() == step[0], "Last move not recorded");
                    TestPlayers.leave(helper, player);
                })
                .thenSucceed();
    }

    /** Stacks changed in place (eating, durability) are found through the slot sync, not only through setItem. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void inPlaceChangesAreDetected(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper);
        UUID uuid = player.getUUID();
        player.getInventory().setItem(0, new ItemStack(Items.BREAD, 10));
        helper.startSequence()
                .thenExecute(() -> TestPlayers.syncSlots(player))
                .thenWaitUntil(() -> assertEntries(uuid, 2, Trigger.CHANGE))
                .thenExecute(() -> {
                    player.getInventory().getItem(0).shrink(3);
                    TestPlayers.syncSlots(player);
                })
                .thenWaitUntil(() -> assertEntries(uuid, 3, Trigger.CHANGE))
                .thenExecute(() -> {
                    helper.assertTrue(latest(uuid).items().get(0).getCount() == 7, "Shrunk stack not recorded");
                    TestPlayers.leave(helper, player);
                })
                .thenSucceed();
    }

    /** The ender chest is part of the same timeline and reports its own changes. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void enderChestChangesAreRecorded(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper);
        UUID uuid = player.getUUID();
        helper.startSequence()
                .thenWaitUntil(() -> assertEntries(uuid, 1, Trigger.JOIN))
                .thenExecute(() -> player.getEnderChestInventory().setItem(26, new ItemStack(Items.DIAMOND, 12)))
                .thenWaitUntil(() -> assertEntries(uuid, 2, Trigger.CHANGE))
                .thenExecute(() -> {
                    helper.assertTrue(latest(uuid).items().get(Slots.ENDER_START + 26).getCount() == 12, "Ender chest not recorded");
                    TestPlayers.leave(helper, player);
                })
                .thenSucceed();
    }

    /** A death is recorded with its cause and the items the player carried, before they drop. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void deathsKeepTheItems(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper);
        UUID uuid = player.getUUID();
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.setHoverName(Component.literal("Last Words"));
        player.getInventory().setItem(4, sword.copy());
        player.getInventory().armor.set(3, new ItemStack(Items.DIAMOND_HELMET));
        player.kill();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Entry death = TestPlayers.entries(uuid).stream().filter(e -> e.trigger() == Trigger.DEATH).findFirst()
                            .orElseThrow(() -> new GameTestAssertException("No death entry"));
                    BackupStore store = TestPlayers.store();
                    StateView state = store.call(() -> store.stateAt(uuid, death.time())).join().orElseThrow();
                    helper.assertTrue(ItemStack.matches(state.items().get(4), sword), "Sword missing from the death state");
                    helper.assertTrue(state.items().get(Slots.ARMOR_START + 3).is(Items.DIAMOND_HELMET), "Helmet missing");
                    helper.assertTrue(death.context().detail() != null, "Death cause missing");
                    helper.assertTrue(!death.context().dimension().isEmpty(), "Death position missing");
                })
                .thenExecute(() -> TestPlayers.leave(helper, player))
                .thenSucceed();
    }

    /** The death alert's restore button names the entry recorded for the death. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void deathAlertsRestoreTheDeathEntry(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper);
        UUID uuid = player.getUUID();
        helper.assertFalse(DeathAlerts.worthTelling(player), "An empty inventory loses nothing");
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
        helper.assertTrue(DeathAlerts.worthTelling(player), "Items would drop");
        GameRules.BooleanValue keep = helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
        boolean before = keep.get();
        keep.set(true, helper.getLevel().getServer());
        boolean kept = DeathAlerts.worthTelling(player);
        keep.set(before, helper.getLevel().getServer());
        helper.assertFalse(kept, "keepInventory loses nothing");
        player.kill();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Entry death = TestPlayers.entries(uuid).stream().filter(e -> e.trigger() == Trigger.DEATH).findFirst()
                            .orElseThrow(() -> new GameTestAssertException("No death entry"));
                    List<String> commands = new ArrayList<>();
                    DeathAlerts.line(player, "test", death.time(), death.context(), true, true).visit((style, text) -> {
                        if (style.getClickEvent() != null) {
                            commands.add(style.getClickEvent().getValue());
                        }
                        return Optional.empty();
                    }, Style.EMPTY);
                    String restore = "/inventory set " + player.getScoreboardName() + " " + death.time();
                    helper.assertTrue(commands.contains(restore), "Restore button missing: " + commands);
                    helper.assertTrue(commands.size() == 3, "Expected view, restore and teleport buttons: " + commands);
                    helper.assertTrue(DeathAlerts.line(player, "test", death.time(), death.context(), true, false).getString()
                            .indexOf('↻') < 0, "No restore button without the restore permission");
                })
                .thenExecute(() -> TestPlayers.leave(helper, player))
                .thenSucceed();
    }

    /** A restore records the state before it and a marker naming the admin, so it can be undone. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void restoresAreFramedByEntries(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper);
        UUID uuid = player.getUUID();
        StateView[] saved = new StateView[1];
        player.getInventory().setItem(0, new ItemStack(Items.GOLDEN_APPLE, 2));
        helper.startSequence()
                .thenExecute(() -> TestPlayers.syncSlots(player))
                .thenWaitUntil(() -> assertEntries(uuid, 2, Trigger.CHANGE))
                .thenExecute(() -> {
                    saved[0] = latest(uuid);
                    player.getInventory().setItem(0, new ItemStack(Items.DIRT, 64));
                    player.getInventory().setItem(1, new ItemStack(Items.DIRT, 64));
                    Restorer.restore(player, saved[0], true, true, "Admin");
                    helper.assertTrue(player.getInventory().getItem(0).is(Items.GOLDEN_APPLE), "Restore not applied");
                    helper.assertTrue(player.getInventory().getItem(1).isEmpty(), "Restore kept newer items");
                })
                .thenWaitUntil(() -> {
                    List<Entry> entries = TestPlayers.entries(uuid);
                    Entry restore = entries.get(entries.size() - 1);
                    helper.assertTrue(restore.trigger() == Trigger.RESTORE, "Restore marker missing");
                    helper.assertTrue("Admin".equals(restore.context().actor()), "Admin not recorded");
                    BackupStore store = TestPlayers.store();
                    StateView before = store.call(() -> store.stateAt(uuid, restore.time() - 1)).join().orElseThrow();
                    helper.assertTrue(before.items().get(1) != null && before.items().get(1).is(Items.DIRT), "State before restore lost");
                })
                .thenExecute(() -> TestPlayers.leave(helper, player))
                .thenSucceed();
    }

    private static void assertEntries(UUID player, int count, Trigger lastTrigger) {
        List<Entry> entries = TestPlayers.entries(player);
        if (entries.size() != count || entries.get(count - 1).trigger() != lastTrigger) {
            throw new GameTestAssertException("Expected " + count + " entries ending with " + lastTrigger + ", got "
                    + entries.stream().map(e -> e.trigger().key()).toList());
        }
    }

    private static StateView latest(UUID player) {
        BackupStore store = TestPlayers.store();
        return store.call(() -> store.latest(player)).join().orElseThrow(() -> new GameTestAssertException("No state stored"));
    }
}
