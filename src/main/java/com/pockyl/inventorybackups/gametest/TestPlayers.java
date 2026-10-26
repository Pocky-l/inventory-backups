package com.pockyl.inventorybackups.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.pockyl.inventorybackups.backup.BackupService;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.Entry;

import java.util.List;
import java.util.UUID;

/**
 * Mock players that join the test server, so the live change tracker and the world's storage see them.
 */
final class TestPlayers {
    private TestPlayers() {
    }

    /**
     * A creative mock player that has logged in. Vanilla's mock player has a connection without a channel on 1.20.1,
     * which Forge's login code cannot handle; this one is connected to an in-memory channel, as on newer versions.
     */
    static ServerPlayer join(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-mock-player")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        channel.attr(Connection.ATTRIBUTE_PROTOCOL).set(ConnectionProtocol.PLAY);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    static BackupStore store() {
        BackupStore store = BackupService.store();
        if (store == null) {
            throw new GameTestAssertException("No backup storage is running");
        }
        return store;
    }

    static List<Entry> entries(UUID player) {
        BackupStore store = store();
        return store.call(() -> store.entries(player, 0, Long.MAX_VALUE)).join();
    }

    /** Mock players are not ticked by a connection; this runs the per-tick slot sync that reports changes. */
    static void syncSlots(ServerPlayer player) {
        player.containerMenu.broadcastChanges();
    }

    /** Logs the player out and deletes their backups. */
    static void leave(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
        BackupStore store = store();
        UUID uuid = player.getUUID();
        store.call(() -> {
            store.deletePlayer(uuid);
            return true;
        }).join();
    }
}
