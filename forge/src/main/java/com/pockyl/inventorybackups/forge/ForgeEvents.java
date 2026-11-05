package com.pockyl.inventorybackups.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.event.CommonEvents;

/**
 * Forwards the game events of Forge to {@link CommonEvents}.
 */
@Mod.EventBusSubscriber(modid = InventoryBackups.MOD_ID)
public final class ForgeEvents {
    private ForgeEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommonEvents.registerCommands(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onGatherPermissionNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(ForgePlatform.NODES.values().toArray(new PermissionNode<?>[0]));
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
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CommonEvents.serverTick(event.getServer());
        }
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

    // The stack of the event is a copy holding what was picked up.
    @SubscribeEvent
    public static void onPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CommonEvents.itemPickedUp(player, event.getOriginalEntity(), event.getStack());
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
