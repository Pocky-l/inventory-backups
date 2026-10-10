package com.pockyl.inventorybackups.backup;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.Retention;

/**
 * Background housekeeping: a retention pass when the server has started and then every few minutes, on the storage
 * thread.
 */
@EventBusSubscriber(modid = InventoryBackups.MOD_ID)
public final class Maintenance {
    private static long nextRun;

    private Maintenance() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        runRetention();
        nextRun = event.getServer().getTickCount() + intervalTicks();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() >= nextRun) {
            nextRun = event.getServer().getTickCount() + intervalTicks();
            runRetention();
        }
    }

    public static Retention.Policy policy() {
        return Retention.Policy.ofDays(Config.KEEP_ALL_HOURS.get(), Config.HOURLY_DAYS.get(), Config.RETENTION_DAYS.get(),
                Config.IMPORTANT_DAYS.get(), Config.PLAYER_BUDGET_MB.get() * 1024L * 1024L, Config.TOTAL_BUDGET_MB.get() * 1024L * 1024L,
                Config.KEYFRAME_INTERVAL.get());
    }

    private static void runRetention() {
        BackupStore store = BackupService.store();
        if (store == null) {
            return;
        }
        Retention.Policy policy = policy();
        store.execute(() -> {
            long start = System.currentTimeMillis();
            Retention.Result result = Retention.run(store, policy, start);
            if (result.rewritten() + result.deleted() > 0) {
                InventoryBackups.LOGGER.info("Backup retention: thinned {} files, deleted {}, removed {} entries, freed {} KiB in {} ms",
                        result.rewritten(), result.deleted(), result.entriesRemoved(), result.bytesFreed() / 1024,
                        System.currentTimeMillis() - start);
            }
        });
    }

    private static long intervalTicks() {
        return Config.CLEANUP_INTERVAL_MINUTES.get() * 60L * 20L;
    }
}
