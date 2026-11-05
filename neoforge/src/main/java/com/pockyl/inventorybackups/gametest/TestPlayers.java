package com.pockyl.inventorybackups.gametest;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
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

    @SuppressWarnings("removal")
    static ServerPlayer join(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
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
