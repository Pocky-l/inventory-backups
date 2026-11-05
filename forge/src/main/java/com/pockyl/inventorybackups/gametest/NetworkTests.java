package com.pockyl.inventorybackups.gametest;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.Slots;
import com.pockyl.inventorybackups.network.EntryRow;
import com.pockyl.inventorybackups.network.PayloadCodec;
import com.pockyl.inventorybackups.network.Payloads;
import com.pockyl.inventorybackups.network.ServerRequests;
import com.pockyl.inventorybackups.storage.Trigger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Payload codecs and the server side of the admin screen: permission checks, validation and answers.
 */
@GameTestHolder(InventoryBackups.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkTests {
    private NetworkTests() {
    }

    @GameTest(template = "empty")
    public static void payloadsSurviveTheWire(GameTestHelper helper) {
        ItemStack named = new ItemStack(Items.DIAMOND_PICKAXE);
        named.setHoverName(Component.literal("Digger"));
        Int2ObjectMap<ItemStack> items = new Int2ObjectOpenHashMap<>();
        items.put(0, named);
        items.put(2026, new ItemStack(Items.DIAMOND, 12));
        Int2LongOpenHashMap since = new Int2LongOpenHashMap();
        since.put(0, 123L);
        EntryRow row = new EntryRow(1000, Trigger.DEATH, 3, "minecraft:overworld", new BlockPos(1, 2, 3), "fell", "", null);
        items.put(Slots.MOD_START, new ItemStack(Items.GOLD_NUGGET));
        Int2ObjectMap<String> keys = new Int2ObjectOpenHashMap<>();
        keys.put(Slots.MOD_START, "curios:ring/0");
        Payloads.StateResponse state = new Payloads.StateResponse(7, UUID.randomUUID(), row, items, null,
                new Int2ObjectOpenHashMap<>(), since, keys, new int[] {Slots.MOD_START, Slots.MOD_START + 1});
        Payloads.StateResponse back = roundTrip(Payloads.StateResponse.STREAM_CODEC, state);
        helper.assertTrue(back.id() == 7 && back.entry().equals(row) && back.since().get(0) == 123L, "State header changed");
        helper.assertTrue(ItemStack.matches(back.items().get(0), named) && back.items().get(2026).getCount() == 12, "Items changed");
        helper.assertTrue("curios:ring/0".equals(back.slotKeys().get(Slots.MOD_START)), "Slot keys changed");
        helper.assertTrue(back.liveSlots().length == 2 && back.liveSlots()[1] == Slots.MOD_START + 1, "Live slots changed");

        Payloads.ActionRequest action = new Payloads.ActionRequest(3, UUID.randomUUID(), 55, Payloads.Action.RESTORE_SLOTS,
                new int[] {0, 100, -106});
        Payloads.ActionRequest actionBack = roundTrip(Payloads.ActionRequest.STREAM_CODEC, action);
        helper.assertTrue(actionBack.action() == Payloads.Action.RESTORE_SLOTS && actionBack.slots().length == 3, "Action changed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void requestsArePermissionChecked(GameTestHelper helper) {
        ServerPlayer admin = TestPlayers.join(helper);
        List<Payloads.Payload> received = new ArrayList<>();
        ServerRequests.setSender((player, payload) -> {
            if (player == admin) {
                received.add(payload);
            }
        });
        admin.getInventory().setItem(0, new ItemStack(Items.EMERALD, 3));
        TestPlayers.syncSlots(admin);
        ModSlotTests.giveSlots(admin.getUUID(), 2);
        ServerRequests.timeline(new Payloads.TimelineRequest(1, admin.getUUID(), 0, Long.MAX_VALUE / 2), admin);
        helper.assertTrue(received.size() == 1 && received.get(0) instanceof Payloads.Notice notice && !notice.ok(), "Request not denied");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(TestPlayers.entries(admin.getUUID()).size() >= 2, "Change not recorded yet"))
                .thenExecute(() -> {
                    received.clear();
                    Config.PERMISSION_LEVEL.set(0);
                    long now = System.currentTimeMillis();
                    ServerRequests.timeline(new Payloads.TimelineRequest(2, admin.getUUID(), now - 3_600_000L, now + 60_000L), admin);
                    ServerRequests.state(new Payloads.StateRequest(3, admin.getUUID(), Long.MAX_VALUE), admin);
                    ServerRequests.timeline(new Payloads.TimelineRequest(4, admin.getUUID(), 10, 5), admin);
                    ServerRequests.action(new Payloads.ActionRequest(5, admin.getUUID(), Long.MAX_VALUE, Payloads.Action.GIVE_COPY,
                            new int[] {0, 99_999}), admin);
                })
                .thenWaitUntil(() -> {
                    Payloads.TimelineResponse timeline = find(received, Payloads.TimelineResponse.class, 2);
                    helper.assertTrue(timeline.entries().size() >= 2 && timeline.entries().get(0).trigger() == Trigger.JOIN,
                            "Timeline wrong");
                    Payloads.StateResponse state = find(received, Payloads.StateResponse.class, 3);
                    helper.assertTrue(state.items().get(0).is(Items.EMERALD), "State wrong");
                    long testSlots = Arrays.stream(state.liveSlots())
                            .filter(slot -> state.slotKeys().getOrDefault(slot, "").startsWith("test:")).count();
                    helper.assertTrue(testSlots == 2, "Empty mod slots of an online player not sent");
                    Payloads.Notice invalid = find(received, Payloads.Notice.class, 4);
                    helper.assertTrue(!invalid.ok(), "Reversed time range accepted");
                    Payloads.Notice copied = find(received, Payloads.Notice.class, 5);
                    helper.assertTrue(copied.ok(), "Copy failed: " + copied.message().getString());
                })
                .thenExecute(() -> {
                    Config.PERMISSION_LEVEL.set(2);
                    ServerRequests.setSender(null);
                    TestPlayers.leave(helper, admin);
                })
                .thenSucceed();
    }

    private static <T extends Payloads.Payload> T find(List<Payloads.Payload> received, Class<T> type, int id) {
        for (Payloads.Payload payload : received) {
            if (type.isInstance(payload)) {
                T found = type.cast(payload);
                int foundId = found instanceof Payloads.Notice notice ? notice.id()
                        : found instanceof Payloads.TimelineResponse timeline ? timeline.id()
                        : found instanceof Payloads.StateResponse state ? state.id() : -1;
                if (foundId == id) {
                    return found;
                }
            }
        }
        throw new GameTestAssertException("No " + type.getSimpleName() + " for request " + id);
    }

    private static <T> T roundTrip(PayloadCodec<T> codec, T value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buf, value);
            return codec.decode(buf);
        } finally {
            buf.release();
        }
    }
}
