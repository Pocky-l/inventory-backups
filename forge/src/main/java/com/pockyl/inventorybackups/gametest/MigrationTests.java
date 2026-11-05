package com.pockyl.inventorybackups.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.Slots;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.Entry;
import com.pockyl.inventorybackups.storage.LegacyImporter;
import com.pockyl.inventorybackups.storage.PlayerInfo;
import com.pockyl.inventorybackups.storage.StateView;
import com.pockyl.inventorybackups.storage.Trigger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Import of the backups of the older 1.20.1 versions (1.2.x JSON, 2.0.x NBT) and compatibility with 2.x config files.
 */
@GameTestHolder(InventoryBackups.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MigrationTests {
    private MigrationTests() {
    }

    @GameTest(template = "empty")
    public static void legacyBackupsAreImportedOnce(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            UUID player = UUID.randomUUID();
            Path legacy = store.root().resolve("InventoryLog");
            Path snapshots = store.root().resolve("invbackup");
            // 1.2.x saved every slot, empty ones as air.
            write(legacy.resolve("inventory").resolve(player.toString()).resolve("2025-01-15-18-30-45.json"),
                    "{\"data\":[{\"index\":0,\"nbt\":\"{Count:5b,id:\\\"minecraft:bread\\\"}\"},"
                            + "{\"index\":1,\"nbt\":\"{Count:0b,id:\\\"minecraft:air\\\"}\"},"
                            + "{\"index\":103,\"nbt\":\"{Count:1b,id:\\\"minecraft:iron_helmet\\\"}\"}]}");
            write(legacy.resolve("enderchest").resolve(player.toString()).resolve("2025-01-15-18-31-00.json"),
                    "{\"data\":[{\"index\":26,\"nbt\":\"{Count:12b,id:\\\"minecraft:diamond\\\"}\"}]}");
            write(legacy.resolve("inventory").resolve(player.toString()).resolve("2025-01-15-18-40-00-death.json"),
                    "{\"data\":[{\"index\":0,\"nbt\":\"{Count:2b,id:\\\"minecraft:bread\\\"}\"},{\"index\":7,\"nbt\":\"broken{\"}]}");
            write(legacy.resolve("inventory").resolve(player.toString()).resolve("2025-01-15-19-00-00.json"), "not json");

            LegacyImporter.Result result = store.call(() -> LegacyImporter.run(store, legacy, snapshots,
                    Config.LegacyAction.ARCHIVE)).join();
            helper.assertTrue(result != null && result.files() == 3 && result.skippedFiles() == 1, "Unexpected import result " + result);
            List<Entry> entries = store.call(() -> store.entries(player, 0, Long.MAX_VALUE)).join();
            helper.assertTrue(entries.size() == 3, "Expected 3 entries, got " + entries.size());
            helper.assertTrue(entries.get(0).trigger() == Trigger.IMPORT && entries.get(2).trigger() == Trigger.DEATH,
                    "Triggers not mapped");
            helper.assertTrue(entries.get(0).time() == millis(2025, 1, 15, 18, 30, 45), "Time not taken from the file name");

            StateView first = store.call(() -> store.stateAt(player, entries.get(0).time())).join().orElseThrow();
            helper.assertTrue(first.items().size() == 2 && first.items().get(Slots.ARMOR_START + 3).is(Items.IRON_HELMET),
                    "Inventory wrong: " + first.items().keySet());
            StateView afterEnder = store.call(() -> store.stateAt(player, entries.get(1).time())).join().orElseThrow();
            helper.assertTrue(afterEnder.items().get(0).getCount() == 5, "Inventory lost when the ender chest was imported");
            helper.assertTrue(afterEnder.items().get(Slots.ENDER_START + 26).is(Items.DIAMOND), "Ender chest not merged");
            StateView death = store.call(() -> store.latest(player)).join().orElseThrow();
            helper.assertTrue(death.items().get(0).getCount() == 2 && !death.items().containsKey(103), "Death state wrong");
            helper.assertTrue(death.items().get(Slots.ENDER_START + 26) != null, "Ender chest not carried forward");

            helper.assertFalse(Files.exists(legacy), "1.2.x folder not archived");
            helper.assertTrue(Files.isDirectory(store.root().resolve("InventoryLog-1.2.x")), "Archive missing");
            helper.assertTrue(store.call(() -> LegacyImporter.run(store, legacy, snapshots, Config.LegacyAction.KEEP)).join() == null,
                    "Import ran twice");
            helper.succeed();
        } catch (IOException e) {
            helper.fail("IO error: " + e.getMessage());
        } finally {
            TestStores.delete(store);
        }
    }

    /**
     * 2.0.x snapshots hold the inventory as a player file does, the ender chest and the Curios capability; a player who
     * used both older versions gets one timeline.
     */
    @GameTest(template = "empty")
    public static void snapshotBackupsAreImported(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            UUID player = UUID.randomUUID();
            Path legacy = store.root().resolve("InventoryLog");
            Path snapshots = store.root().resolve("invbackup");
            long older = millis(2025, 1, 15, 18, 30, 45);
            write(legacy.resolve("inventory").resolve(player.toString()).resolve("2025-01-15-18-30-45.json"),
                    "{\"data\":[{\"index\":0,\"nbt\":\"{Count:5b,id:\\\"minecraft:bread\\\"}\"}]}");
            long periodic = older + 3_600_000L;
            long died = periodic + 60_000L;
            ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
            named.setHoverName(Component.literal("Heirloom"));

            CompoundTag first = snapshot(player, "Periodic Backup");
            ListTag inventory = new ListTag();
            inventory.add(item(0, named));
            inventory.add(item(103, new ItemStack(Items.IRON_HELMET)));
            inventory.add(item(150, new ItemStack(Items.SHIELD)));
            inventory.add(item(200, new ItemStack(Items.LEATHER)));
            first.put("Inventory", inventory);
            ListTag ender = new ListTag();
            ender.add(item(26, new ItemStack(Items.DIAMOND, 12)));
            first.put("EnderChest", ender);
            CompoundTag caps = new CompoundTag();
            caps.put("curios:inventory", curios("ring", 1, new ItemStack(Items.GOLD_NUGGET), new ItemStack(Items.EMERALD)));
            first.put("ForgeCaps", caps);
            writeSnapshot(snapshots.resolve(player.toString()).resolve("save_" + periodic + ".nbt"), first);

            CompoundTag second = snapshot(player, "Death Backup");
            ListTag after = new ListTag();
            after.add(item(4, new ItemStack(Items.APPLE, 3)));
            second.put("Inventory", after);
            second.put("EnderChest", new ListTag());
            writeSnapshot(snapshots.resolve(player.toString()).resolve("save_" + died + ".nbt"), second);
            write(snapshots.resolve(player.toString()).resolve("save_garbage.nbt"), "x");

            LegacyImporter.Result result = store.call(() -> LegacyImporter.run(store, legacy, snapshots,
                    Config.LegacyAction.ARCHIVE)).join();
            helper.assertTrue(result != null && result.files() == 3 && result.skippedFiles() == 1 && result.curioSlots() == 0,
                    "Unexpected import result " + result);
            List<Entry> entries = store.call(() -> store.entries(player, 0, Long.MAX_VALUE)).join();
            helper.assertTrue(entries.size() == 3, "Expected 3 entries, got " + entries.size());
            helper.assertTrue(entries.get(0).time() == older && entries.get(1).time() == periodic && entries.get(2).time() == died,
                    "Sources not merged in time order");
            helper.assertTrue("1.2.x inventory auto".equals(entries.get(0).context().detail())
                    && "2.0.x Periodic Backup".equals(entries.get(1).context().detail()), "Details wrong");
            helper.assertTrue(entries.get(1).trigger() == Trigger.IMPORT && entries.get(2).trigger() == Trigger.DEATH,
                    "Reasons not mapped");

            StateView periodicState = store.call(() -> store.stateAt(player, periodic)).join().orElseThrow();
            helper.assertTrue(ItemStack.matches(periodicState.items().get(0), named), "Main slot wrong");
            helper.assertTrue(periodicState.items().get(Slots.ARMOR_START + 3).is(Items.IRON_HELMET), "Armor slot wrong");
            helper.assertTrue(periodicState.items().get(Slots.OFFHAND).is(Items.SHIELD), "Offhand wrong");
            helper.assertTrue(periodicState.items().get(Slots.ENDER_START + 26).getCount() == 12, "Ender chest wrong");
            int ring = store.slotKeys().find("curios:ring/1");
            int cosmetic = store.slotKeys().find("curios:cosmetic/ring/0");
            int backpack = store.slotKeys().find("backpacked:back/0");
            helper.assertTrue(ring >= Slots.MOD_START && periodicState.items().get(ring).is(Items.GOLD_NUGGET), "Curio not imported");
            helper.assertTrue(cosmetic >= Slots.MOD_START && periodicState.items().get(cosmetic).is(Items.EMERALD),
                    "Cosmetic curio not imported");
            helper.assertTrue(backpack >= Slots.MOD_START && periodicState.items().get(backpack).is(Items.LEATHER),
                    "Backpacked slot not imported");
            helper.assertTrue(periodicState.items().size() == 7, "Unexpected slots: " + periodicState.items().keySet());

            StateView death = store.call(() -> store.latest(player)).join().orElseThrow();
            helper.assertTrue(death.items().size() == 1 && death.items().get(4).getCount() == 3, "A snapshot must replace the state");
            PlayerInfo info = store.playerInfo(player);
            helper.assertTrue(info != null && "Steve".equals(info.name()), "Name not taken from the snapshot");

            helper.assertTrue(Files.isDirectory(store.root().resolve("invbackup-2.0.x"))
                    && Files.isDirectory(store.root().resolve("InventoryLog-1.2.x")), "Folders not archived");
            helper.succeed();
        } catch (IOException e) {
            helper.fail("IO error: " + e.getMessage());
        } finally {
            TestStores.delete(store);
        }
    }

    /** The keys of 2.x config files stay part of the spec, so old files load without being reset. */
    @GameTest(template = "empty")
    public static void legacyConfigKeysStayValid(GameTestHelper helper) {
        for (String key : List.of("general.tickSaveEnabled", "general.preservationPeriod", "general.deadSaveEnabled",
                "general.joinSaveEnabled", "general.quitSaveEnabled", "general.retentionDays", "enderchest.enderChestEnabled",
                "enderchest.enderChestTickSaveEnabled", "enderchest.enderChestDeadSaveEnabled", "enderchest.enderChestJoinSaveEnabled",
                "enderchest.enderChestQuitSaveEnabled", "enderchest.enderChestOpenSaveEnabled")) {
            helper.assertTrue(Config.SPEC.getSpec().contains(key), "2.x config key missing: " + key);
        }
        helper.succeed();
    }

    private static long millis(int year, int month, int day, int hour, int minute, int second) {
        return LocalDateTime.of(year, month, day, hour, minute, second).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** The fields 2.0.x wrote around the inventory. */
    private static CompoundTag snapshot(UUID player, String reason) {
        CompoundTag tag = new CompoundTag();
        tag.putString("uuid", player.toString());
        tag.putString("name", "Steve");
        tag.putString("timestamp", "2025-01-15-19-30-45");
        tag.putString("reason", reason);
        return tag;
    }

    private static CompoundTag item(int slot, ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putByte("Slot", (byte) slot);
        stack.save(tag);
        return tag;
    }

    /** A saved Curios inventory with one slot type, an item in one slot and one in the first cosmetic slot. */
    private static CompoundTag curios(String type, int slot, ItemStack stack, ItemStack cosmetic) {
        CompoundTag handler = new CompoundTag();
        handler.putInt("SavedBaseSize", slot + 1);
        handler.put("Stacks", stacks(slot + 1, slot, stack));
        handler.put("Cosmetics", stacks(slot + 1, 0, cosmetic));
        CompoundTag entry = new CompoundTag();
        entry.putString("Identifier", type);
        entry.put("StacksHandler", handler);
        ListTag list = new ListTag();
        list.add(entry);
        CompoundTag inventory = new CompoundTag();
        inventory.put("Curios", list);
        return inventory;
    }

    /** An item handler as Forge saves it. */
    private static CompoundTag stacks(int size, int slot, ItemStack stack) {
        CompoundTag item = new CompoundTag();
        item.putInt("Slot", slot);
        stack.save(item);
        ListTag items = new ListTag();
        items.add(item);
        CompoundTag stacks = new CompoundTag();
        stacks.put("Items", items);
        stacks.putInt("Size", size);
        return stacks;
    }

    private static void writeSnapshot(Path file, CompoundTag tag) throws IOException {
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(tag, file.toFile());
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
