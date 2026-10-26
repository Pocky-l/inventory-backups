package com.pockyl.inventorybackups.network;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import com.pockyl.inventorybackups.InventoryBackups;

import java.util.function.BiConsumer;

/**
 * Registers the payloads on one channel. It is optional both ways, so clients without the mod can join a server with it
 * and the other way round; both sides check {@link #present} before sending.
 */
public final class Network {
    public static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(InventoryBackups.id("main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
    private static int nextId;

    private Network() {
    }

    public static void register() {
        toServer(Payloads.OpenRequest.class, Payloads.OpenRequest.STREAM_CODEC, ServerRequests::open);
        toServer(Payloads.PlayersRequest.class, Payloads.PlayersRequest.STREAM_CODEC, ServerRequests::players);
        toServer(Payloads.TimelineRequest.class, Payloads.TimelineRequest.STREAM_CODEC, ServerRequests::timeline);
        toServer(Payloads.StateRequest.class, Payloads.StateRequest.STREAM_CODEC, ServerRequests::state);
        toServer(Payloads.ActionRequest.class, Payloads.ActionRequest.STREAM_CODEC, ServerRequests::action);
        toServer(Payloads.SearchRequest.class, Payloads.SearchRequest.STREAM_CODEC, ServerRequests::search);

        toClient(Payloads.Hello.class, Payloads.Hello.STREAM_CODEC);
        toClient(Payloads.Notice.class, Payloads.Notice.STREAM_CODEC);
        toClient(Payloads.PlayersResponse.class, Payloads.PlayersResponse.STREAM_CODEC);
        toClient(Payloads.TimelineResponse.class, Payloads.TimelineResponse.STREAM_CODEC);
        toClient(Payloads.StateResponse.class, Payloads.StateResponse.STREAM_CODEC);
        toClient(Payloads.SearchResponse.class, Payloads.SearchResponse.STREAM_CODEC);
    }

    /** Whether the other end of a connection has the channel: it runs the mod. */
    public static boolean present(Connection connection) {
        return CHANNEL.isRemotePresent(connection);
    }

    public static void sendToPlayer(ServerPlayer player, Payloads.Payload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToServer(Payloads.Payload payload) {
        CHANNEL.sendToServer(payload);
    }

    /** Handled on the server thread, only for messages from a player. */
    private static <T extends Payloads.Payload> void toServer(Class<T> type, PayloadCodec<T> codec, BiConsumer<T, ServerPlayer> handler) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buf) -> codec.encode(buf, payload))
                .decoder(codec::decode)
                .consumerMainThread((payload, context) -> {
                    ServerPlayer sender = context.get().getSender();
                    if (sender != null) {
                        handler.accept(payload, sender);
                    }
                })
                .add();
    }

    /** Handled on the client thread by whatever the client part installed in {@link ClientBridge}. */
    private static <T extends Payloads.Payload> void toClient(Class<T> type, PayloadCodec<T> codec) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buf) -> codec.encode(buf, payload))
                .decoder(codec::decode)
                .consumerMainThread((payload, context) -> ClientBridge.receive(payload))
                .add();
    }
}
