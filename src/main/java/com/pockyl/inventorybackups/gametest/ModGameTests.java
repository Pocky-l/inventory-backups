package com.pockyl.inventorybackups.gametest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.BackupData;
import com.pockyl.inventorybackups.backup.BackupKind;
import com.pockyl.inventorybackups.backup.BackupService;
import com.pockyl.inventorybackups.backup.BackupStorage;
import com.pockyl.inventorybackups.backup.PlayerSlots;
import com.pockyl.inventorybackups.ui.ChatUI;
import com.pockyl.inventorybackups.ui.Texts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * In-game tests, run headless by {@code gradlew runGameTestServer}.
 * Tests use the 1x1x1 {@code empty} structure unless they need a prepared one.
 */
@GameTestHolder(InventoryBackups.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModGameTests {
    private ModGameTests() {
    }

    @GameTest(template = "empty")
    public static void modLoads(GameTestHelper helper) {
        helper.succeed();
    }

    /** Every slot type survives save, load and restore, including item components. */
    @GameTest(template = "empty")
    public static void inventoryBackupRoundTrip(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        try {
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.set(DataComponents.CUSTOM_NAME, Component.literal("Backup Blade"));
            sword.setDamageValue(42);
            player.getInventory().items.set(5, sword.copy());
            player.getInventory().items.set(35, new ItemStack(Items.COBBLESTONE, 37));
            player.getInventory().armor.set(3, new ItemStack(Items.IRON_HELMET));
            player.getInventory().offhand.set(0, new ItemStack(Items.SHIELD));

            String name = BackupService.save(BackupKind.INVENTORY, player, BackupService.DEATH, false)
                    .orElseThrow(() -> new GameTestAssertException("Backup was not written"));
            helper.assertTrue(name.endsWith("-death"), "Death suffix missing: " + name);
            helper.assertTrue(BackupStorage.list(BackupKind.INVENTORY, player.getUUID()).contains(name), "Backup not listed");

            player.getInventory().clearContent();
            BackupData loaded = BackupStorage.load(BackupKind.INVENTORY, player.getUUID(), name)
                    .orElseThrow(() -> new GameTestAssertException("Backup could not be read"));
            PlayerSlots.restore(BackupKind.INVENTORY, player, loaded.decode(player.registryAccess()));

            helper.assertTrue(ItemStack.matches(player.getInventory().items.get(5), sword), "Sword not restored with its data");
            helper.assertTrue(player.getInventory().items.get(35).getCount() == 37, "Stack size not restored");
            helper.assertTrue(player.getInventory().armor.get(3).is(Items.IRON_HELMET), "Helmet not restored");
            helper.assertTrue(player.getInventory().offhand.get(0).is(Items.SHIELD), "Offhand not restored");
            helper.succeed();
        } finally {
            deleteBackups(player.getUUID());
        }
    }

    @GameTest(template = "empty")
    public static void enderChestBackupRoundTrip(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        try {
            player.getEnderChestInventory().setItem(26, new ItemStack(Items.DIAMOND, 12));
            String name = BackupService.save(BackupKind.ENDER_CHEST, player, BackupService.OPEN, false)
                    .orElseThrow(() -> new GameTestAssertException("Ender chest backup was not written"));
            player.getEnderChestInventory().clearContent();
            player.getEnderChestInventory().setItem(0, new ItemStack(Items.DIRT));

            BackupData loaded = BackupStorage.load(BackupKind.ENDER_CHEST, player.getUUID(), name)
                    .orElseThrow(() -> new GameTestAssertException("Ender chest backup could not be read"));
            PlayerSlots.restore(BackupKind.ENDER_CHEST, player, loaded.decode(player.registryAccess()));

            helper.assertTrue(player.getEnderChestInventory().getItem(0).isEmpty(), "Restore did not clear newer items");
            helper.assertTrue(player.getEnderChestInventory().getItem(26).getCount() == 12, "Diamonds not restored");
            helper.succeed();
        } finally {
            deleteBackups(player.getUUID());
        }
    }

    @GameTest(template = "empty")
    public static void periodicSavesSkipUnchangedAndEmpty(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        try {
            helper.assertTrue(BackupService.save(BackupKind.INVENTORY, player, null, true).isEmpty(), "Empty inventory was saved");
            player.getInventory().items.set(0, new ItemStack(Items.APPLE, 3));
            helper.assertTrue(BackupService.save(BackupKind.INVENTORY, player, null, true).isPresent(), "First save skipped");
            helper.assertTrue(BackupService.save(BackupKind.INVENTORY, player, null, true).isEmpty(), "Unchanged inventory saved again");
            player.getInventory().items.set(1, new ItemStack(Items.BREAD));
            helper.assertTrue(BackupService.save(BackupKind.INVENTORY, player, null, true).isPresent(), "Changed inventory not saved");
            helper.succeed();
        } finally {
            BackupService.forget(player.getUUID());
            deleteBackups(player.getUUID());
        }
    }

    @GameTest(template = "empty")
    public static void backupNamesCannotLeaveTheFolder(GameTestHelper helper) {
        UUID player = UUID.randomUUID();
        helper.assertTrue(BackupStorage.isValidName("2025-01-15-18-30-45-death"), "Normal name rejected");
        helper.assertFalse(BackupStorage.isValidName("../../config/x"), "Path traversal accepted");
        helper.assertFalse(BackupStorage.isValidName(""), "Empty name accepted");
        helper.assertTrue(BackupStorage.load(BackupKind.INVENTORY, player, "../" + player).isEmpty(), "Unsafe name loaded");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void listFiltersAndScreenLayout(GameTestHelper helper) {
        String death = "2025-01-15-18-30-45-death";
        helper.assertTrue(ChatUI.matches(death, ""), "Empty filter must match");
        helper.assertTrue(ChatUI.matches(death, "2025-01"), "Month filter must match");
        helper.assertTrue(ChatUI.matches(death, "death"), "Type filter must match");
        helper.assertFalse(ChatUI.matches(death, "join"), "Wrong type matched");
        helper.assertFalse(ChatUI.matches("2025-01-15-18-30-45", "2024"), "Wrong year matched");

        helper.assertTrue(PlayerSlots.screenSlot(BackupKind.INVENTORY, PlayerSlots.ARMOR_START, 54) == 36, "Armor slot misplaced");
        helper.assertTrue(PlayerSlots.screenSlot(BackupKind.INVENTORY, PlayerSlots.OFFHAND, 54) == 40, "Offhand misplaced");
        helper.assertTrue(PlayerSlots.screenSlot(BackupKind.INVENTORY, 1000, 54) == 41, "First curio misplaced");
        helper.assertTrue(PlayerSlots.screenSlot(BackupKind.INVENTORY, 1013, 54) == -1, "Overflowing curio not dropped");
        helper.succeed();
    }

    /** Chat texts carry the English text, so players without the mod do not see raw keys. */
    @GameTest(template = "empty")
    public static void chatTextsHaveEnglishFallback(GameTestHelper helper) {
        TranslatableContents contents = (TranslatableContents) Texts.tr("inventorybackups.prefix").getContents();
        helper.assertTrue("Backups".equals(contents.getFallback()), "Missing fallback: " + contents.getFallback());
        helper.succeed();
    }

    @SuppressWarnings("removal")
    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
    }

    private static void deleteBackups(UUID player) {
        for (BackupKind kind : BackupKind.values()) {
            Path dir = BackupStorage.directory(kind, player);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException e) {
                InventoryBackups.LOGGER.warn("Could not delete test backups in {}", dir, e);
            }
        }
    }
}
