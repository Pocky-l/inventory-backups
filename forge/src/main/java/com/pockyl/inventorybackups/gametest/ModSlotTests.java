package com.pockyl.inventorybackups.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.BackupKind;
import com.pockyl.inventorybackups.backup.ChangeTracker;
import com.pockyl.inventorybackups.backup.Restorer;
import com.pockyl.inventorybackups.backup.Slots;
import com.pockyl.inventorybackups.compat.ModSlots;
import com.pockyl.inventorybackups.compat.SlotProvider;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.Entry;
import com.pockyl.inventorybackups.storage.EntryContext;
import com.pockyl.inventorybackups.storage.SlotKeys;
import com.pockyl.inventorybackups.storage.StateView;
import com.pockyl.inventorybackups.storage.Trigger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * The slots of other mods, through a provider of this test: numbering, change detection, restores into changed slot
 * layouts, deaths that empty the slots early and the chat preview.
 */
@GameTestHolder(InventoryBackups.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModSlotTests {
    private static final int TIMEOUT = 400;
    private static final TestSlots SLOTS = new TestSlots();

    static {
        ModSlots.addProvider(SLOTS);
        // Stands in for mods that take items out of their slots when the player dies, before the drops.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingDeathEvent.class, event -> {
            if (SLOTS.clearOnDeath.contains(event.getEntity().getUUID())) {
                SLOTS.players.getOrDefault(event.getEntity().getUUID(), Map.of()).replaceAll((slot, stack) -> ItemStack.EMPTY);
            }
        });
    }

    private ModSlotTests() {
    }

    /** Gives a test player this many empty test mod slots, for tests of other classes. */
    static void giveSlots(UUID player, int count) {
        SLOTS.give(player, count);
    }

    /** A slot keeps its number for the world, also after a restart, and numbers are never handed out twice. */
    @GameTest(template = "empty")
    public static void slotNumbersAreStable(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            SlotKeys keys = store.slotKeys();
            int ring = keys.number("curios:ring/0");
            int hat = keys.number("accessories:hat/0");
            helper.assertTrue(ring == Slots.MOD_START && hat == Slots.MOD_START + 1, "Numbers not handed out in order");
            helper.assertTrue(keys.number("curios:ring/0") == ring, "Number of a known slot changed");
            helper.assertTrue(keys.find("toolbelt:belt") == -1, "Unknown slot found");
            store.close();
            BackupStore reopened = new BackupStore(store.root(), helper.getLevel().registryAccess(), () -> 4);
            SlotKeys loaded = reopened.slotKeys();
            helper.assertTrue(loaded.find("accessories:hat/0") == hat, "Numbers lost on restart");
            helper.assertTrue("curios:ring/0".equals(loaded.key(ring)), "Key of a number lost on restart");
            helper.assertTrue(loaded.number("toolbelt:belt") == Slots.MOD_START + 2, "Number handed out twice");
            reopened.close();
        } finally {
            TestStores.delete(store);
        }
        helper.succeed();
    }

    /** Mod slots have no change events: the periodic scan finds the change, and a restore puts the item back. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void modSlotsAreRecordedAndRestored(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper);
        UUID uuid = player.getUUID();
        SLOTS.give(uuid, 2);
        StateView[] saved = new StateView[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!TestPlayers.entries(uuid).isEmpty(), "No join entry"))
                .thenExecute(() -> SLOTS.players.get(uuid).put("charm/1", new ItemStack(Items.TOTEM_OF_UNDYING)))
                .thenWaitUntil(() -> {
                    saved[0] = latest(uuid);
                    helper.assertTrue(itemInSlot(saved[0], "test:charm/1").is(Items.TOTEM_OF_UNDYING), "Mod slot change not recorded");
                })
                .thenExecute(() -> {
                    SLOTS.players.get(uuid).put("charm/1", new ItemStack(Items.DIRT));
                    SLOTS.players.get(uuid).put("charm/0", new ItemStack(Items.STICK));
                    Restorer.restore(player, saved[0], true, false, "Admin");
                    helper.assertTrue(SLOTS.players.get(uuid).get("charm/1").is(Items.TOTEM_OF_UNDYING), "Mod slot not restored");
                    helper.assertTrue(SLOTS.players.get(uuid).get("charm/0").isEmpty(), "Newer item kept in a mod slot");
                    TestPlayers.leave(helper, player);
                })
                .thenSucceed();
    }

    /** A stored item whose slot is gone (the slot type shrank or its mod was removed) goes into the inventory. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void itemsOfMissingSlotsGoToTheInventory(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper);
        UUID uuid = player.getUUID();
        SLOTS.give(uuid, 3);
        SLOTS.players.get(uuid).put("charm/2", new ItemStack(Items.NAUTILUS_SHELL));
        StateView[] saved = new StateView[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!TestPlayers.entries(uuid).isEmpty(), "No join entry"))
                .thenExecute(() -> {
                    ChangeTracker.capture(player, Trigger.COMMAND, EntryContext.of(player));
                })
                .thenWaitUntil(() -> {
                    saved[0] = latest(uuid);
                    helper.assertTrue(itemInSlot(saved[0], "test:charm/2").is(Items.NAUTILUS_SHELL), "Snapshot misses the mod slot");
                })
                .thenExecute(() -> {
                    SLOTS.give(uuid, 1);
                    Restorer.restore(player, saved[0], true, false, "Admin");
                    helper.assertTrue(player.getInventory().contains(new ItemStack(Items.NAUTILUS_SHELL)), "Item of a missing slot lost");
                    TestPlayers.leave(helper, player);
                })
                .thenSucceed();
    }

    /** Items a mod moves out of its slots during the death are still in the death entry. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void deathsKeepModSlots(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper);
        UUID uuid = player.getUUID();
        SLOTS.give(uuid, 1);
        SLOTS.clearOnDeath.add(uuid);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!TestPlayers.entries(uuid).isEmpty(), "No join entry"))
                .thenExecute(() -> {
                    SLOTS.players.get(uuid).put("charm/0", new ItemStack(Items.HEART_OF_THE_SEA));
                    player.kill();
                    helper.assertTrue(SLOTS.players.get(uuid).get("charm/0").isEmpty(), "Test listener did not empty the slot");
                })
                .thenWaitUntil(() -> {
                    Entry death = TestPlayers.entries(uuid).stream().filter(e -> e.trigger() == Trigger.DEATH).findFirst()
                            .orElseThrow(() -> new GameTestAssertException("No death entry"));
                    BackupStore store = TestPlayers.store();
                    StateView state = store.call(() -> store.stateAt(uuid, death.time())).join().orElseThrow();
                    helper.assertTrue(itemInSlot(state, "test:charm/0").is(Items.HEART_OF_THE_SEA), "Mod slot missing from the death");
                })
                .thenExecute(() -> {
                    SLOTS.clearOnDeath.remove(uuid);
                    TestPlayers.leave(helper, player);
                })
                .thenSucceed();
    }

    /** The chat preview shows mod slots after the offhand. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void previewShowsModSlots(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper);
        UUID uuid = player.getUUID();
        SLOTS.give(uuid, 1);
        SLOTS.players.get(uuid).put("charm/0", new ItemStack(Items.ECHO_SHARD));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(itemInSlot(latest(uuid), "test:charm/0").is(Items.ECHO_SHARD),
                        "Join entry misses the mod slot"))
                .thenExecute(() -> {
                    helper.assertTrue(BackupKind.INVENTORY.preview(latest(uuid).items()).get(41).is(Items.ECHO_SHARD),
                            "Mod slot not in the preview");
                    TestPlayers.leave(helper, player);
                })
                .thenSucceed();
    }

    private static ItemStack itemInSlot(StateView state, String key) {
        int number = TestPlayers.store().slotKeys().find(key);
        return number < 0 ? ItemStack.EMPTY : state.items().getOrDefault(number, ItemStack.EMPTY);
    }

    private static StateView latest(UUID player) {
        BackupStore store = TestPlayers.store();
        return store.call(() -> store.latest(player)).join().orElseThrow(() -> new GameTestAssertException("No state stored"));
    }

    /** Slots {@code charm/<index>} for the test players that asked for them; other players have none. */
    private static final class TestSlots implements SlotProvider {
        final Map<UUID, Map<String, ItemStack>> players = new ConcurrentHashMap<>();
        final Set<UUID> clearOnDeath = ConcurrentHashMap.newKeySet();

        /** Gives the player this many slots; items in slots beyond them are dropped. */
        void give(UUID player, int count) {
            Map<String, ItemStack> slots = players.computeIfAbsent(player, uuid -> new ConcurrentHashMap<>());
            slots.keySet().removeIf(slot -> SlotProvider.indexed(slot).index() >= count);
            for (int i = 0; i < count; i++) {
                slots.putIfAbsent("charm/" + i, ItemStack.EMPTY);
            }
        }

        @Override
        public String id() {
            return "test";
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public void read(Player player, BiConsumer<String, ItemStack> slots) {
            Map<String, ItemStack> own = players.get(player.getUUID());
            if (own != null) {
                own.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> slots.accept(e.getKey(), e.getValue()));
            }
        }

        @Override
        public ItemStack get(Player player, String slot) {
            Map<String, ItemStack> own = players.get(player.getUUID());
            return own == null ? ItemStack.EMPTY : own.getOrDefault(slot, ItemStack.EMPTY);
        }

        @Override
        public boolean set(Player player, String slot, ItemStack stack) {
            Map<String, ItemStack> own = players.get(player.getUUID());
            if (own == null || !own.containsKey(slot)) {
                return false;
            }
            own.put(slot, stack);
            return true;
        }
    }
}
