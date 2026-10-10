package com.pockyl.inventorybackups.gametest;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.network.EntryRow;
import com.pockyl.inventorybackups.network.Payloads;
import com.pockyl.inventorybackups.network.ServerRequests;
import com.pockyl.inventorybackups.storage.Trigger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

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
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Digger"));
        Int2ObjectMap<ItemStack> items = new Int2ObjectOpenHashMap<>();
        items.put(0, named);
        items.put(2026, new ItemStack(Items.DIAMOND, 12));
        Int2LongOpenHashMap since = new Int2LongOpenHashMap();
        since.put(0, 123L);
        EntryRow row = new EntryRow(1000, Trigger.DEATH, 3, "minecraft:overworld", new BlockPos(1, 2, 3), "fell", "", null);
        Payloads.StateResponse state = new Payloads.StateResponse(7, UUID.randomUUID(), row, items, null,
                new Int2ObjectOpenHashMap<>(), since);
        Payloads.StateResponse back = roundTrip(helper, Payloads.StateResponse.STREAM_CODEC, state);
        helper.assertTrue(back.id() == 7 && back.entry().equals(row) && back.since().get(0) == 123L, "State header changed");
        helper.assertTrue(ItemStack.matches(back.items().get(0), named) && back.items().get(2026).getCount() == 12, "Items changed");

        Payloads.ActionRequest action = new Payloads.ActionRequest(3, UUID.randomUUID(), 55, Payloads.Action.RESTORE_SLOTS,
                new int[] {0, 100, -106});
        Payloads.ActionRequest actionBack = roundTrip(helper, Payloads.ActionRequest.STREAM_CODEC, action);
        helper.assertTrue(actionBack.action() == Payloads.Action.RESTORE_SLOTS && actionBack.slots().length == 3, "Action changed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void requestsArePermissionChecked(GameTestHelper helper) {
        ServerPlayer admin = TestPlayers.join(helper);
        List<CustomPacketPayload> received = new ArrayList<>();
        ServerRequests.setSender((player, payload) -> {
            if (player == admin) {
                received.add(payload);
            }
        });
        admin.getInventory().setItem(0, new ItemStack(Items.EMERALD, 3));
        TestPlayers.syncSlots(admin);
        IPayloadContext context = context(admin);
        ServerRequests.timeline(new Payloads.TimelineRequest(1, admin.getUUID(), 0, Long.MAX_VALUE / 2), context);
        helper.assertTrue(received.size() == 1 && received.get(0) instanceof Payloads.Notice notice && !notice.ok(), "Request not denied");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(TestPlayers.entries(admin.getUUID()).size() >= 2, "Change not recorded yet"))
                .thenExecute(() -> {
                    received.clear();
                    Config.PERMISSION_LEVEL.set(0);
                    long now = System.currentTimeMillis();
                    ServerRequests.timeline(new Payloads.TimelineRequest(2, admin.getUUID(), now - 3_600_000L, now + 60_000L), context);
                    ServerRequests.state(new Payloads.StateRequest(3, admin.getUUID(), Long.MAX_VALUE), context);
                    ServerRequests.timeline(new Payloads.TimelineRequest(4, admin.getUUID(), 10, 5), context);
                    ServerRequests.action(new Payloads.ActionRequest(5, admin.getUUID(), Long.MAX_VALUE, Payloads.Action.GIVE_COPY,
                            new int[] {0, 99_999}), context);
                })
                .thenWaitUntil(() -> {
                    Payloads.TimelineResponse timeline = find(received, Payloads.TimelineResponse.class, 2);
                    helper.assertTrue(timeline.entries().size() >= 2 && timeline.entries().get(0).trigger() == Trigger.JOIN,
                            "Timeline wrong");
                    Payloads.StateResponse state = find(received, Payloads.StateResponse.class, 3);
                    helper.assertTrue(state.items().get(0).is(Items.EMERALD), "State wrong");
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

    private static <T extends CustomPacketPayload> T find(List<CustomPacketPayload> received, Class<T> type, int id) {
        for (CustomPacketPayload payload : received) {
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

    private static <T> T roundTrip(GameTestHelper helper, StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(),
                ConnectionType.NEOFORGE);
        try {
            codec.encode(buf, value);
            return codec.decode(buf);
        } finally {
            buf.release();
        }
    }

    /** Just enough of a payload context for the server handlers, which only ask for the player. */
    private static IPayloadContext context(ServerPlayer player) {
        return new IPayloadContext() {
            @Override
            public ICommonPacketListener listener() {
                return player.connection;
            }

            @Override
            public Player player() {
                return player;
            }

            @Override
            public CompletableFuture<Void> enqueueWork(Runnable task) {
                task.run();
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
                return CompletableFuture.completedFuture(task.get());
            }

            @Override
            public PacketFlow flow() {
                return PacketFlow.SERVERBOUND;
            }

            @Override
            public void handle(CustomPacketPayload payload) {
            }

            @Override
            public void finishCurrentTask(ConfigurationTask.Type type) {
            }
        };
    }
}
