package com.pockyl.inventorybackups.event;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import com.pockyl.inventorybackups.backup.ChangeTracker;
import com.pockyl.inventorybackups.backup.FlowTracker;
import com.pockyl.inventorybackups.backup.Maintenance;
import com.pockyl.inventorybackups.backup.OfflinePlayers;
import com.pockyl.inventorybackups.command.BackupCommands;
import com.pockyl.inventorybackups.command.SearchCommands;

import java.util.Collection;

/**
 * The game events the mod reacts to, called by the loader projects from their own events (or mixins where the loader
 * has none). Each method keeps the order of the handlers that need one.
 */
public final class CommonEvents {
    private CommonEvents() {
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        BackupCommands.register(dispatcher);
        SearchCommands.register(dispatcher);
    }

    public static void serverStarting(MinecraftServer server) {
        ChangeTracker.onServerStarting(server);
    }

    public static void serverStarted(MinecraftServer server) {
        Maintenance.onServerStarted(server);
    }

    public static void serverStopping() {
        OfflinePlayers.onServerStopping();
    }

    public static void serverStopped() {
        ChangeTracker.onServerStopped();
        FlowTracker.onServerStopped();
    }

    /** At the end of every server tick. */
    public static void serverTick(MinecraftServer server) {
        ChangeTracker.onServerTick(server);
        Maintenance.onServerTick(server);
    }

    /** Before any other mod handles the login. */
    public static void beforeJoin(ServerPlayer player) {
        OfflinePlayers.onLogin(player);
    }

    public static void playerJoined(ServerPlayer player) {
        ChangeTracker.onJoin(player);
    }

    public static void playerLeft(ServerPlayer player) {
        ChangeTracker.onQuit(player);
    }

    /** The new player entity after a respawn or a return from the End. */
    public static void playerRespawned(ServerPlayer player) {
        ChangeTracker.onRespawn(player);
    }

    /** Before any other mod handles the death: they may move items away (graves, backpacks). */
    public static void beforeDeath(ServerPlayer player) {
        ChangeTracker.beforeDeath(player);
    }

    /** After every other mod handled the death, and only when none canceled it. */
    public static void death(ServerPlayer player, DamageSource source) {
        ChangeTracker.onDeath(player, source);
    }

    public static void deathDrops(ServerPlayer player, Collection<ItemEntity> drops) {
        FlowTracker.onDrops(player, drops);
    }

    public static void itemTossed(ServerPlayer player, ItemEntity entity) {
        FlowTracker.onToss(player, entity);
    }

    /** @param picked what was picked up */
    public static void itemPickedUp(ServerPlayer player, ItemEntity entity, ItemStack picked) {
        FlowTracker.onPickup(player, entity, picked);
    }

    public static void rightClickBlock(ServerPlayer player, BlockPos pos) {
        ChangeTracker.onRightClickBlock(player, pos);
    }

    public static void containerOpened(ServerPlayer player, AbstractContainerMenu menu) {
        ChangeTracker.onContainerOpen(player, menu);
    }

    public static void containerClosed(ServerPlayer player, AbstractContainerMenu menu) {
        ChangeTracker.onContainerClose(player, menu);
    }
}
