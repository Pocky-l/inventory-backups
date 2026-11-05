package com.pockyl.inventorybackups.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.index.ItemIndex;
import com.pockyl.inventorybackups.index.ItemKeys;
import com.pockyl.inventorybackups.index.Presence;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.ItemCodec;
import com.pockyl.inventorybackups.storage.Trigger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static com.pockyl.inventorybackups.gametest.TestStores.capture;
import static com.pockyl.inventorybackups.gametest.TestStores.slots;

/**
 * The item index: holders over time, fingerprints that survive wear, names and enchantments, persistence and rebuild.
 */
@GameTestHolder(InventoryBackups.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IndexTests {
    private static final long T0 = 1_760_000_000_000L;

    private IndexTests() {
    }

    @GameTest(template = "empty")
    public static void holdersAreTrackedOverTime(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();
            ItemStack blade = blade(helper, 10);
            ItemIndex index = attach(store, helper);
            fill(store, alice, bob, blade);
            store.awaitIdle();

            String fingerprint = fingerprintOf(store, blade);
            store.call(() -> {
                checkHolders(helper, index, alice, bob, fingerprint);
                return true;
            }).join();
            helper.succeed();
        } finally {
            TestStores.delete(store);
        }
    }

    @GameTest(template = "empty")
    public static void indexIsSavedAndRebuilt(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        BackupStore reopened = null;
        try {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();
            ItemStack blade = blade(helper, 10);
            attach(store, helper);
            fill(store, alice, bob, blade);
            String fingerprint = fingerprintOf(store, blade);
            store.close();
            Path file = store.root().resolve("index.dat");
            helper.assertTrue(Files.isRegularFile(file), "Index not saved on close");

            reopened = new BackupStore(store.root(), helper.getLevel().registryAccess(), () -> 4);
            ItemIndex loaded = attach(reopened, helper);
            reopened.call(() -> {
                checkHolders(helper, loaded, alice, bob, fingerprint);
                return true;
            }).join();
            reopened.close();

            Files.delete(file);
            reopened = new BackupStore(store.root(), helper.getLevel().registryAccess(), () -> 4);
            ItemIndex rebuilt = attach(reopened, helper);
            reopened.call(() -> {
                checkHolders(helper, rebuilt, alice, bob, fingerprint);
                return true;
            }).join();
            helper.succeed();
        } catch (IOException e) {
            helper.fail("IO error: " + e.getMessage());
        } finally {
            if (reopened != null) {
                reopened.close();
            }
            TestStores.delete(store);
        }
    }

    /** Alice holds diamonds and the blade, then hands the worn blade to Bob. */
    private static void fill(BackupStore store, UUID alice, UUID bob, ItemStack blade) {
        store.submit(capture(alice, T0, Trigger.JOIN, true, slots(0, new ItemStack(Items.DIAMOND, 5), 1, blade)));
        store.submit(capture(alice, T0 + 60_000, Trigger.CHANGE, false, slots(0, ItemStack.EMPTY, 1, ItemStack.EMPTY)));
        ItemStack worn = blade.copy();
        worn.setDamageValue(200);
        store.submit(capture(bob, T0 + 65_000, Trigger.JOIN, true, slots(3, worn)));
        store.awaitIdle();
    }

    private static void checkHolders(GameTestHelper helper, ItemIndex index, UUID alice, UUID bob, String fingerprint) {
        ItemIndex.KeyEntry diamonds = index.get("i:minecraft:diamond");
        helper.assertTrue(diamonds != null, "Diamonds not indexed");
        Presence aliceDiamonds = diamonds.holders().get(alice);
        helper.assertTrue(aliceDiamonds != null && !aliceDiamonds.holdsNow(), "Alice still holds the diamonds");
        helper.assertTrue(aliceDiamonds.first() == T0 && aliceDiamonds.last() == T0 + 60_000, "Diamond range wrong");
        helper.assertTrue(aliceDiamonds.heldAt(T0 + 30_000) && !aliceDiamonds.heldAt(T0 + 61_000), "heldAt wrong");
        helper.assertTrue(aliceDiamonds.max() == 5, "Max count wrong");

        ItemIndex.KeyEntry unique = index.get(fingerprint);
        helper.assertTrue(unique != null, "Blade fingerprint not indexed");
        helper.assertTrue("backup blade".equals(unique.name()), "Name not indexed: " + unique.name());
        helper.assertTrue(unique.enchantments().contains("minecraft:sharpness"), "Enchantment not indexed: " + unique.enchantments());
        helper.assertTrue(unique.holders().get(bob) != null && unique.holders().get(bob).holdsNow(), "Worn blade lost its fingerprint");
        helper.assertTrue(!unique.holders().get(alice).holdsNow(), "Alice still holds the blade");
    }

    private static ItemIndex attach(BackupStore store, GameTestHelper helper) {
        ItemIndex index = new ItemIndex(store.root().resolve("index.dat"), helper.getLevel().registryAccess());
        store.execute(() -> index.loadOrRebuild(store));
        store.addListener(index);
        return index;
    }

    private static String fingerprintOf(BackupStore store, ItemStack stack) {
        return ItemKeys.of(ItemCodec.encode(store.registries(), stack).tag(), store.registries()).fingerprintKey();
    }

    private static ItemStack blade(GameTestHelper helper, int damage) {
        ItemStack blade = new ItemStack(Items.DIAMOND_SWORD);
        blade.setHoverName(Component.literal("Backup Blade"));
        blade.setDamageValue(damage);
        blade.enchant(Enchantments.SHARPNESS, 5);
        return blade;
    }
}
