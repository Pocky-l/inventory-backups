package com.pockyl.inventorybackups.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.BackupKind;
import com.pockyl.inventorybackups.backup.BackupService;

/**
 * The moments a backup is taken: periodically, on death, on join and quit, and optionally when an ender chest opens.
 */
@EventBusSubscriber(modid = InventoryBackups.MOD_ID)
public final class BackupEvents {
    private BackupEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long periodTicks = Config.PRESERVATION_PERIOD.get() * 20L;
        // tickCount staggers the saves of different players instead of writing everyone in the same tick.
        if (periodTicks <= 0 || player.tickCount % periodTicks != 0) {
            return;
        }
        if (Config.TICK_SAVE_ENABLED.get()) {
            BackupService.save(BackupKind.INVENTORY, player, null, true);
        }
        if (Config.ENDER_CHEST_TICK_SAVE_ENABLED.get()) {
            BackupService.save(BackupKind.ENDER_CHEST, player, null, true);
        }
    }

    // Highest priority: the inventory must be saved before death drops empty it.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (Config.DEAD_SAVE_ENABLED.get()) {
            BackupService.save(BackupKind.INVENTORY, player, BackupService.DEATH, false);
        }
        if (Config.ENDER_CHEST_DEAD_SAVE_ENABLED.get()) {
            BackupService.save(BackupKind.ENDER_CHEST, player, BackupService.DEATH, false);
        }
    }

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (Config.JOIN_SAVE_ENABLED.get()) {
            BackupService.save(BackupKind.INVENTORY, player, BackupService.JOIN, false);
        }
        if (Config.ENDER_CHEST_JOIN_SAVE_ENABLED.get()) {
            BackupService.save(BackupKind.ENDER_CHEST, player, BackupService.JOIN, false);
        }
    }

    @SubscribeEvent
    public static void onQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (Config.QUIT_SAVE_ENABLED.get()) {
            BackupService.save(BackupKind.INVENTORY, player, BackupService.QUIT, false);
        }
        if (Config.ENDER_CHEST_QUIT_SAVE_ENABLED.get()) {
            BackupService.save(BackupKind.ENDER_CHEST, player, BackupService.QUIT, false);
        }
        BackupService.forget(player.getUUID());
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!Config.ENDER_CHEST_OPEN_SAVE_ENABLED.get() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Only the player's own ender chest, not every container of the same size.
        if (event.getContainer() instanceof ChestMenu menu && menu.getContainer() == player.getEnderChestInventory()) {
            BackupService.save(BackupKind.ENDER_CHEST, player, BackupService.OPEN, false);
        }
    }
}
