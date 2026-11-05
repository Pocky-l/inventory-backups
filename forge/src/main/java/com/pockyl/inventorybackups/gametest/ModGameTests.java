package com.pockyl.inventorybackups.gametest;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.BackupKind;
import com.pockyl.inventorybackups.backup.Slots;
import com.pockyl.inventorybackups.command.EntrySelector;
import com.pockyl.inventorybackups.storage.Entry;
import com.pockyl.inventorybackups.storage.EntryContext;
import com.pockyl.inventorybackups.storage.Trigger;
import com.pockyl.inventorybackups.ui.Texts;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.OptionalLong;

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

    /** Chat texts carry the English text, so players without the mod do not see raw keys. */
    @GameTest(template = "empty")
    public static void chatTextsHaveEnglishFallback(GameTestHelper helper) {
        TranslatableContents contents = (TranslatableContents) Texts.tr("inventorybackups.prefix").getContents();
        helper.assertTrue("Backups".equals(contents.getFallback()), "Missing fallback: " + contents.getFallback());
        helper.succeed();
    }

    /** Entry names typed in commands: buttons use milliseconds, admins can type 2.x style timestamps. */
    @GameTest(template = "empty")
    public static void entryNamesAndFilters(GameTestHelper helper) {
        helper.assertTrue(EntrySelector.parse("1760000000000").equals(OptionalLong.of(1_760_000_000_000L)), "Millis not parsed");
        helper.assertTrue(EntrySelector.parse("latest").equals(OptionalLong.of(Long.MAX_VALUE)), "latest not parsed");
        long second = LocalDateTime.of(2025, 1, 15, 18, 30, 45).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        helper.assertTrue(EntrySelector.parse("2025-01-15-18-30-45-death").equals(OptionalLong.of(second + 999)), "2.x name not parsed");
        helper.assertTrue(EntrySelector.parse("../../config").isEmpty(), "Garbage parsed");
        helper.assertTrue(EntrySelector.parse("99999999999999999999").isEmpty(), "Overflow parsed");

        Entry death = new Entry(second, Trigger.DEATH, EntryContext.NONE, false, 3, new int[0], new int[0], new int[0]);
        helper.assertTrue(EntrySelector.matches(death, ""), "Empty filter must match");
        helper.assertTrue(EntrySelector.matches(death, "2025-01"), "Month filter must match");
        helper.assertTrue(EntrySelector.matches(death, "death"), "Trigger filter must match");
        helper.assertFalse(EntrySelector.matches(death, "join"), "Wrong trigger matched");
        helper.assertFalse(EntrySelector.matches(death, "2024"), "Wrong year matched");
        helper.succeed();
    }

    /** The chest preview reads like the player screen: main rows, hotbar, armor from the helmet down, offhand, mod slots. */
    @GameTest(template = "empty")
    public static void previewLayout(GameTestHelper helper) {
        BackupKind inventory = BackupKind.INVENTORY;
        helper.assertTrue(inventory.chestSlot(9) == 0, "Main inventory misplaced");
        helper.assertTrue(inventory.chestSlot(0) == 27, "Hotbar misplaced");
        helper.assertTrue(inventory.chestSlot(Slots.ARMOR_START + 3) == 36, "Helmet misplaced");
        helper.assertTrue(inventory.chestSlot(Slots.ARMOR_START) == 39, "Boots misplaced");
        helper.assertTrue(inventory.chestSlot(Slots.OFFHAND) == 40, "Offhand misplaced");
        Int2ObjectMap<ItemStack> items = new Int2ObjectOpenHashMap<>();
        items.put(Slots.MOD_START + 7, new ItemStack(Items.EMERALD));
        items.put(Slots.CURIOS_START, new ItemStack(Items.DIAMOND));
        for (int i = 0; i < 20; i++) {
            items.put(Slots.MOD_START + 100 + i, new ItemStack(Items.STICK));
        }
        Int2ObjectMap<ItemStack> chest = inventory.preview(items);
        helper.assertTrue(chest.get(41).is(Items.DIAMOND), "2.x curio not first among the mod slots");
        helper.assertTrue(chest.get(42).is(Items.EMERALD), "Mod slots not in slot order");
        helper.assertTrue(chest.size() == 13 && !chest.containsKey(54), "Overflowing mod slots not dropped");
        helper.assertTrue(inventory.chestSlot(Slots.ENDER_START) == -1, "Ender chest shown in the inventory");
        helper.assertTrue(BackupKind.ENDER_CHEST.chestSlot(Slots.ENDER_START + 26) == 26, "Ender chest misplaced");
        helper.assertTrue(BackupKind.ENDER_CHEST.chestSlot(5) == -1, "Inventory shown in the ender chest");
        helper.assertTrue(BlockPos.ZERO.equals(EntryContext.NONE.pos()), "Context default changed");
        helper.succeed();
    }
}
