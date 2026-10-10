package com.pockyl.inventorybackups.event;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.BackupKind;
import com.pockyl.inventorybackups.backup.BackupStorage;
import com.pockyl.inventorybackups.command.BackupCommands;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Commands and the removal of expired backups (on server start and then every hour).
 */
@EventBusSubscriber(modid = InventoryBackups.MOD_ID)
public final class ServerEvents {
    private static final int CLEANUP_INTERVAL_TICKS = 20 * 60 * 60;

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BackupCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        deleteExpiredBackups();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CLEANUP_INTERVAL_TICKS == 0) {
            deleteExpiredBackups();
        }
    }

    private static void deleteExpiredBackups() {
        Instant cutoff = Instant.now().minus(Config.RETENTION_DAYS.get(), ChronoUnit.DAYS);
        int inventories = BackupStorage.deleteOlderThan(BackupKind.INVENTORY, cutoff);
        int enderChests = BackupStorage.deleteOlderThan(BackupKind.ENDER_CHEST, cutoff);
        if (inventories + enderChests > 0) {
            InventoryBackups.LOGGER.info("Deleted {} inventory and {} ender chest backups older than {} days",
                    inventories, enderChests, Config.RETENTION_DAYS.get());
        }
    }
}
