package com.pockyl.inventorybackups.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.OfflinePlayers;
import com.pockyl.inventorybackups.backup.Restorer;
import com.pockyl.inventorybackups.menu.LivePlayerMenu;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.Entry;
import com.pockyl.inventorybackups.storage.StateView;
import com.pockyl.inventorybackups.storage.Trigger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Restoring and editing players who are offline, through their save file.
 */
@GameTestHolder(InventoryBackups.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OfflineTests {
    private static final int TIMEOUT = 400;

    private OfflineTests() {
    }

    /** A restore of an offline player lands in their save file and is recorded with a restore marker. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void offlinePlayersCanBeRestored(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = TestPlayers.join(helper);
        UUID uuid = player.getUUID();
        String name = player.getScoreboardName();
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        StateView[] saved = new StateView[1];
        helper.startSequence()
                .thenExecute(() -> TestPlayers.syncSlots(player))
                .thenWaitUntil(() -> {
                    saved[0] = latest(uuid);
                    helper.assertTrue(saved[0].items().containsKey(0), "Sword not recorded");
                })
                .thenExecute(() -> {
                    player.getInventory().setItem(0, new ItemStack(Items.DIRT, 5));
                    // Logging out saves the player file with the dirt.
                    server.getPlayerList().remove(player);
                    ServerPlayer offline = OfflinePlayers.acquire(server, uuid, name)
                            .orElseThrow(() -> new GameTestAssertException("Offline player not loaded"));
                    helper.assertTrue(offline.getInventory().getItem(0).is(Items.DIRT), "Save file not loaded");
                    Restorer.restore(offline, saved[0], true, false, "Admin");
                    OfflinePlayers.release(offline);
                    ServerPlayer reloaded = OfflinePlayers.acquire(server, uuid, name).orElseThrow();
                    helper.assertTrue(reloaded != offline, "Released copy kept");
                    helper.assertTrue(reloaded.getInventory().getItem(0).is(Items.DIAMOND_SWORD), "Restore not in the save file");
                    OfflinePlayers.release(reloaded);
                })
                .thenWaitUntil(() -> {
                    List<Entry> entries = TestPlayers.entries(uuid);
                    Entry last = entries.get(entries.size() - 1);
                    helper.assertTrue(last.trigger() == Trigger.RESTORE && "Admin".equals(last.context().actor()), "Restore not recorded");
                })
                .thenExecute(() -> forget(helper, uuid))
                .thenSucceed();
    }

    /** The live editor works on the save file of an offline player and records the edit. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void offlinePlayersCanBeEdited(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer admin = TestPlayers.join(helper);
        ServerPlayer player = TestPlayers.join(helper);
        UUID uuid = player.getUUID();
        String name = player.getScoreboardName();
        player.getEnderChestInventory().setItem(3, new ItemStack(Items.EMERALD, 7));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!TestPlayers.entries(uuid).isEmpty(), "No join entry"))
                .thenExecute(() -> {
                    server.getPlayerList().remove(player);
                    ServerPlayer offline = OfflinePlayers.acquire(server, uuid, name).orElseThrow();
                    LivePlayerMenu.open(admin, offline, true, Component.literal("test"));
                    helper.assertTrue(admin.containerMenu instanceof LivePlayerMenu, "Editor not opened");
                    admin.containerMenu.getSlot(3).set(ItemStack.EMPTY);
                    admin.containerMenu.getSlot(4).set(new ItemStack(Items.GOLD_INGOT, 2));
                    ServerPlayer shared = OfflinePlayers.acquire(server, uuid, name).orElseThrow();
                    helper.assertTrue(shared == offline, "Second user got another copy");
                    OfflinePlayers.release(shared);
                    admin.closeContainer();
                    ServerPlayer reloaded = OfflinePlayers.acquire(server, uuid, name).orElseThrow();
                    helper.assertTrue(reloaded != offline, "Copy kept after the editor closed");
                    helper.assertTrue(reloaded.getEnderChestInventory().getItem(3).isEmpty(), "Taken item still in the save file");
                    helper.assertTrue(reloaded.getEnderChestInventory().getItem(4).is(Items.GOLD_INGOT), "Added item not saved");
                    OfflinePlayers.release(reloaded);
                })
                .thenWaitUntil(() -> {
                    List<Entry> entries = TestPlayers.entries(uuid);
                    helper.assertTrue(entries.get(entries.size() - 1).trigger() == Trigger.EDIT, "Edit not recorded");
                })
                .thenExecute(() -> {
                    forget(helper, uuid);
                    TestPlayers.leave(helper, admin);
                })
                .thenSucceed();
    }

    /** Players without a save file cannot be loaded. */
    @GameTest(template = "empty")
    public static void unknownPlayersAreRefused(GameTestHelper helper) {
        UUID stranger = UUID.randomUUID();
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertFalse(OfflinePlayers.hasSaveFile(server, stranger), "Stranger has a save file");
        helper.assertTrue(OfflinePlayers.acquire(server, stranger, "stranger").isEmpty(), "Stranger loaded");
        helper.succeed();
    }

    private static StateView latest(UUID player) {
        BackupStore store = TestPlayers.store();
        return store.call(() -> store.latest(player)).join().orElseThrow(() -> new GameTestAssertException("No state stored"));
    }

    /** Deletes the backups and the save file of a test player who already left. */
    private static void forget(GameTestHelper helper, UUID uuid) {
        BackupStore store = TestPlayers.store();
        store.call(() -> {
            store.deletePlayer(uuid);
            return true;
        }).join();
        Path dir = helper.getLevel().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
        try {
            Files.deleteIfExists(dir.resolve(uuid + ".dat"));
            Files.deleteIfExists(dir.resolve(uuid + ".dat_old"));
        } catch (IOException e) {
            InventoryBackups.LOGGER.warn("Could not delete the save file of test player {}", uuid, e);
        }
    }
}
