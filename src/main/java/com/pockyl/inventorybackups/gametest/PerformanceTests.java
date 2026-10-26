package com.pockyl.inventorybackups.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.PerfStats;
import com.pockyl.inventorybackups.storage.BackupStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures the main-thread cost of change tracking and the bytes stored per change with players who keep moving items.
 * Runs in its own batch, so other tests do not add to the numbers. Targets: well under 0.05 ms per player and tick on
 * average, a few hundred bytes per change.
 */
@GameTestHolder(InventoryBackups.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerformanceTests {
    private static final int PLAYERS = 10;
    private static final int TICKS = 400;
    /** Ticks between two changes of one player; longer than the default settle time of 2.5 seconds. */
    private static final int INTERVAL = 60;

    private PerformanceTests() {
    }

    @GameTest(template = "empty", batch = "performance", timeoutTicks = TICKS + 300)
    public static void trackingIsCheap(GameTestHelper helper) {
        List<ServerPlayer> players = new ArrayList<>();
        for (int i = 0; i < PLAYERS; i++) {
            ServerPlayer player = TestPlayers.join(helper);
            fill(player);
            players.add(player);
        }
        BackupStore store = TestPlayers.store();
        BackupStore.Stats[] baseline = new BackupStore.Stats[1];
        int[] tick = {0};
        helper.startSequence()
                .thenExecute(() -> players.forEach(TestPlayers::syncSlots))
                .thenIdle(80)
                .thenExecute(() -> {
                    store.awaitIdle();
                    baseline[0] = store.stats();
                    PerfStats.reset();
                })
                .thenExecuteFor(TICKS, () -> {
                    // Each player changes a slot every three seconds, at different moments: one entry per change.
                    for (int i = 0; i < players.size(); i++) {
                        if ((tick[0] + i * 7) % INTERVAL == 0) {
                            ServerPlayer player = players.get(i);
                            int slot = (tick[0] / INTERVAL + i) % 36;
                            ItemStack stack = player.getInventory().getItem(slot);
                            player.getInventory().setItem(slot, stack.isEmpty() ? new ItemStack(Items.COBBLESTONE, 1 + i)
                                    : ItemStack.EMPTY);
                        }
                    }
                    // Vanilla runs this sync for every player every tick; mock players are not ticked by a connection.
                    players.forEach(TestPlayers::syncSlots);
                    tick[0]++;
                })
                .thenIdle(80)
                .thenExecute(() -> {
                    store.awaitIdle();
                    PerfStats.Snapshot perf = PerfStats.snapshot();
                    BackupStore.Stats now = store.stats();
                    long entries = now.changeEntries() - baseline[0].changeEntries();
                    long bytes = now.changeBytes() - baseline[0].changeBytes();
                    long perChange = entries == 0 ? 0 : bytes / entries;
                    InventoryBackups.LOGGER.info("Performance: {} ms per player and tick, {} ms per capture, {} change entries, {} bytes "
                            + "per change", String.format("%.5f", perf.millisPerPlayerTick()),
                            String.format("%.4f", perf.millisPerCapture()), entries, perChange);
                    helper.assertTrue(perf.millisPerPlayerTick() < 0.05, "Too slow: " + perf.millisPerPlayerTick() + " ms per player tick");
                    helper.assertTrue(entries > 0 && perChange < 600, "Too large: " + perChange + " bytes per change");
                    helper.assertTrue(entries <= PLAYERS * (TICKS / INTERVAL + 2), "Changes were not coalesced: " + entries);
                    players.forEach(player -> TestPlayers.leave(helper, player));
                })
                .thenSucceed();
    }

    /** A lived-in inventory: tools with components, stacks, armor, an ender chest. */
    private static void fill(ServerPlayer player) {
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND_SWORD));
        player.getInventory().setItem(2, new ItemStack(Items.TORCH, 64));
        player.getInventory().setItem(3, new ItemStack(Items.BREAD, 32));
        for (int slot = 9; slot < 30; slot++) {
            player.getInventory().setItem(slot, new ItemStack(slot % 2 == 0 ? Items.STONE : Items.OAK_PLANKS, 64));
        }
        player.getInventory().armor.set(0, new ItemStack(Items.IRON_BOOTS));
        player.getInventory().armor.set(3, new ItemStack(Items.IRON_HELMET));
        for (int slot = 0; slot < 27; slot += 3) {
            player.getEnderChestInventory().setItem(slot, new ItemStack(Items.DIAMOND, 1 + slot));
        }
    }
}
