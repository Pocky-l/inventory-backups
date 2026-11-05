package com.pockyl.inventorybackups.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

/**
 * The payloads, registered by each loader through {@link Registrar}. They are optional, so clients without the mod
 * can join a server with it and the other way round; both sides check the channel before sending.
 */
public final class Network {
    public static final String VERSION = "1";

    private Network() {
    }

    /** The loader's payload registration; server handlers run on the server thread, client ones on the client thread. */
    public interface Registrar {
        <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type,
                                                      StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                      BiConsumer<T, ServerPlayer> handler);

        <T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type,
                                                      StreamCodec<? super RegistryFriendlyByteBuf, T> codec);
    }

    public static void register(Registrar registrar) {
        registrar.toServer(Payloads.OpenRequest.TYPE, Payloads.OpenRequest.STREAM_CODEC, ServerRequests::open);
        registrar.toServer(Payloads.PlayersRequest.TYPE, Payloads.PlayersRequest.STREAM_CODEC, ServerRequests::players);
        registrar.toServer(Payloads.TimelineRequest.TYPE, Payloads.TimelineRequest.STREAM_CODEC, ServerRequests::timeline);
        registrar.toServer(Payloads.StateRequest.TYPE, Payloads.StateRequest.STREAM_CODEC, ServerRequests::state);
        registrar.toServer(Payloads.ActionRequest.TYPE, Payloads.ActionRequest.STREAM_CODEC, ServerRequests::action);
        registrar.toServer(Payloads.SearchRequest.TYPE, Payloads.SearchRequest.STREAM_CODEC, ServerRequests::search);

        registrar.toClient(Payloads.Hello.TYPE, Payloads.Hello.STREAM_CODEC);
        registrar.toClient(Payloads.Notice.TYPE, Payloads.Notice.STREAM_CODEC);
        registrar.toClient(Payloads.PlayersResponse.TYPE, Payloads.PlayersResponse.STREAM_CODEC);
        registrar.toClient(Payloads.TimelineResponse.TYPE, Payloads.TimelineResponse.STREAM_CODEC);
        registrar.toClient(Payloads.StateResponse.TYPE, Payloads.StateResponse.STREAM_CODEC);
        registrar.toClient(Payloads.SearchResponse.TYPE, Payloads.SearchResponse.STREAM_CODEC);
    }
}
