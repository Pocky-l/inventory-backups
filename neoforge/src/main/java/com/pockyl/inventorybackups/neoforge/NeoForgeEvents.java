package com.pockyl.inventorybackups.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.event.CommonEvents;

/**
 * Forwards the game events of NeoForge to {@link CommonEvents}.
 */
@EventBusSubscriber(modid = InventoryBackups.MOD_ID)
public final class NeoForgeEvents {
    private NeoForgeEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommonEvents.registerCommands(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onGatherPermissionNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(NeoForgePlatform.NODES.values().toArray(new PermissionNode<?>[0]));
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        CommonEvents.serverStarting(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        CommonEvents.serverStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CommonEvents.serverStopping();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CommonEvents.serverStopped();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        CommonEvents.serverTick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CommonEvents.beforeJoin(player);
        }
    }

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CommonEvents.playerJoined(player);
        }
    }

    @SubscribeEvent
    public static void onQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CommonEvents.playerLeft(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CommonEvents.playerRespawned(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CommonEvents.beforeDeath(player);
        }
    }

    // Lowest priority and not for canceled events: only real deaths.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CommonEvents.death(player, event.getSource());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CommonEvents.deathDrops(player, event.getDrops());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            CommonEvents.itemTossed(player, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            CommonEvents.itemPickedUp(player, event.getItemEntity(), event.getOriginalStack(), event.getCurrentStack());
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CommonEvents.rightClickBlock(player, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CommonEvents.containerOpened(player, event.getContainer());
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CommonEvents.containerClosed(player, event.getContainer());
        }
    }
}
