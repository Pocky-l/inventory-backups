package com.pockyl.inventorybackups.gametest;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.command.SearchCommands;
import com.pockyl.inventorybackups.index.ItemIndex;
import com.pockyl.inventorybackups.index.ItemKeys;
import com.pockyl.inventorybackups.index.SearchQuery;
import com.pockyl.inventorybackups.index.SearchResult;
import com.pockyl.inventorybackups.index.SearchService;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.Capture;
import com.pockyl.inventorybackups.storage.EntryContext;
import com.pockyl.inventorybackups.storage.Flow;
import com.pockyl.inventorybackups.storage.FlowDraft;
import com.pockyl.inventorybackups.storage.FlowType;
import com.pockyl.inventorybackups.storage.ItemCodec;
import com.pockyl.inventorybackups.storage.Trigger;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import static com.pockyl.inventorybackups.gametest.TestStores.slots;

/**
 * Item search: query syntax, holders, exact links through containers and drops, probable links and container history.
 */
@GameTestHolder(InventoryBackups.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SearchTests {
    private static final long T0 = 1_760_000_000_000L;
    private static final String WORLD = "minecraft:overworld";
    private static final BlockPos CHEST = new BlockPos(10, 64, 10);

    private SearchTests() {
    }

    @GameTest(template = "empty")
    public static void queriesAndDurationsParse(GameTestHelper helper) {
        helper.assertTrue(new SearchQuery(SearchQuery.Kind.ITEM, "minecraft:diamond").equals(SearchQuery.parse("diamond")), "Item query");
        helper.assertTrue(new SearchQuery(SearchQuery.Kind.NAME, "excalibur").equals(SearchQuery.parse("name: Excalibur")), "Name query");
        helper.assertTrue(new SearchQuery(SearchQuery.Kind.ENCHANTMENT, "minecraft:sharpness").equals(SearchQuery.parse("ench:sharpness")),
                "Enchantment query");
        helper.assertTrue(new SearchQuery(SearchQuery.Kind.FINGERPRINT, "1a2b").equals(SearchQuery.parse("#1A2B")), "Fingerprint query");
        helper.assertTrue(SearchQuery.parse("") == null && SearchQuery.parse("#xyz") == null, "Garbage accepted");
        helper.assertTrue(SearchCommands.Durations.parse("3d").equals(OptionalLong.of(3 * 86_400_000L)), "Duration not parsed");
        helper.assertTrue(SearchCommands.Durations.parse("").equals(OptionalLong.of(0)), "Empty duration");
        helper.assertTrue(SearchCommands.Durations.parse("5x").isEmpty() && SearchCommands.Durations.parse("-1d").isEmpty(),
                "Bad duration");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void searchFindsHoldersAndTransfers(GameTestHelper helper) {
        BackupStore store = TestStores.create(helper);
        try {
            ItemIndex index = new ItemIndex(store.root().resolve("index.dat"), store.registries());
            store.execute(() -> index.loadOrRebuild(store));
            store.addListener(index);
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();
            UUID carol = UUID.randomUUID();
            UUID dave = UUID.randomUUID();
            ItemStack crown = new ItemStack(Items.GOLDEN_HELMET);
            crown.set(DataComponents.CUSTOM_NAME, Component.literal("Crown of Spawn"));
            ItemStack relic = new ItemStack(Items.TOTEM_OF_UNDYING);
            relic.set(DataComponents.CUSTOM_NAME, Component.literal("Relic"));

            // Alice puts the crown into a chest, Bob takes it out.
            store.submit(capture(alice, "Alice", T0, Trigger.JOIN, true, new BlockPos(0, 64, 0), slots(0, crown)));
            store.submitFlows(List.of(flow(T0 + 1000, FlowType.PUT, alice, "Alice", crown, null, null)));
            store.submit(capture(alice, "Alice", T0 + 1000, Trigger.CONTAINER, false, new BlockPos(0, 64, 0), slots(0, ItemStack.EMPTY)));
            store.submitFlows(List.of(flow(T0 + 5000, FlowType.TAKE, bob, "Bob", crown, null, null)));
            store.submit(capture(bob, "Bob", T0 + 5000, Trigger.JOIN, true, new BlockPos(1, 64, 0), slots(3, crown)));
            // Carol drops emeralds, Dave picks them up.
            store.submitFlows(List.of(flow(T0 + 7000, FlowType.TOSS, carol, "Carol", new ItemStack(Items.EMERALD, 4), null, null),
                    flow(T0 + 8000, FlowType.PICKUP, dave, "Dave", new ItemStack(Items.EMERALD, 4), carol, "Carol")));
            // The relic leaves Carol and shows up at Dave three seconds later, a few blocks away, with no event explaining it.
            store.submit(capture(carol, "Carol", T0 + 10_000, Trigger.JOIN, true, new BlockPos(100, 70, 100), slots(1, relic)));
            store.submit(capture(carol, "Carol", T0 + 20_000, Trigger.CHANGE, false, new BlockPos(100, 70, 100),
                    slots(1, ItemStack.EMPTY)));
            store.submit(capture(dave, "Dave", T0 + 23_000, Trigger.JOIN, true, new BlockPos(103, 70, 101), slots(2, relic)));
            store.awaitIdle();

            SearchService.Options options = new SearchService.Options(null, 0, 0, 0, 100, 15_000, 24);
            SearchResult byName = store.call(() -> SearchService.search(store, index, SearchQuery.parse("name:crown"), options)).join();
            helper.assertTrue(byName.items().size() == 1, "Crown not found by name");
            helper.assertTrue(byName.holders().size() == 2 && byName.holders().get(0).player().equals(bob)
                    && byName.holders().get(0).holdsNow() && !byName.holders().get(1).holdsNow(), "Holders wrong: " + byName.holders());
            SearchResult.TrailLink viaChest = byName.trail().stream().filter(l -> l.kind() == SearchResult.LinkKind.CONTAINER)
                    .findFirst().orElse(null);
            helper.assertTrue(viaChest != null && alice.equals(viaChest.from()) && bob.equals(viaChest.to()), "Chest link missing");

            String fingerprint = ItemKeys.of(ItemCodec.encode(store.registries(), crown).tag(), store.registries()).fingerprintKey();
            SearchResult traced = store.call(() -> SearchService.search(store, index,
                    SearchQuery.fingerprint(fingerprint.substring(2)), options)).join();
            helper.assertTrue(traced.holders().size() == 2, "Trace by fingerprint failed");

            SearchResult emeralds = store.call(() -> SearchService.search(store, index, SearchQuery.parse("emerald"), options)).join();
            helper.assertTrue(emeralds.trail().stream().anyMatch(l -> l.kind() == SearchResult.LinkKind.DROP && carol.equals(l.from())
                    && dave.equals(l.to()) && l.count() == 4), "Drop link missing: " + emeralds.trail());

            SearchResult relics = store.call(() -> SearchService.search(store, index, SearchQuery.parse("name:relic"), options)).join();
            helper.assertTrue(relics.trail().stream().anyMatch(l -> l.kind() == SearchResult.LinkKind.PROBABLE && carol.equals(l.from())
                    && dave.equals(l.to()) && l.gapMillis() == 3000), "Probable link missing: " + relics.trail());

            SearchService.Options before = new SearchService.Options(null, 0, 0, T0 + 500, 100, 15_000, 24);
            SearchResult early = store.call(() -> SearchService.search(store, index, SearchQuery.parse("name:crown"), before)).join();
            helper.assertTrue(early.holders().size() == 1 && early.holders().get(0).player().equals(alice), "Alice held it first");
            SearchService.Options between = new SearchService.Options(null, 0, 0, T0 + 2000, 100, 15_000, 24);
            SearchResult chestTime = store.call(() -> SearchService.search(store, index, SearchQuery.parse("name:crown"), between)).join();
            helper.assertTrue(chestTime.holders().isEmpty(), "Nobody held the crown while it was in the chest");

            List<Flow> chest = store.call(() -> SearchService.containerHistory(store, WORLD, CHEST, 0, 10)).join();
            helper.assertTrue(chest.size() == 2 && chest.get(0).type() == FlowType.TAKE, "Container history wrong: " + chest);
            helper.succeed();
        } finally {
            TestStores.delete(store);
        }
    }

    private static Capture capture(UUID player, String name, long time, Trigger trigger, boolean full, BlockPos pos,
                                   Int2ObjectMap<ItemStack> changes) {
        return new Capture(player, name, time, trigger, new EntryContext(WORLD, pos, null, null, null), full, true, changes);
    }

    private static FlowDraft flow(long time, FlowType type, UUID player, String name, ItemStack stack, UUID source, String sourceName) {
        boolean container = type == FlowType.PUT || type == FlowType.TAKE;
        return new FlowDraft(time, type, player, name, stack.copy(), WORLD, CHEST, source, sourceName,
                container ? "minecraft:chest" : null, container ? CHEST : null);
    }
}
