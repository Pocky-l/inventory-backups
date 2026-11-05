package com.pockyl.inventorybackups.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

/**
 * The payloads, registered by each loader through {@link Registrar} on one channel with a number per payload in this
 * order. The channel is optional both ways, so clients without the mod can join a server with it and the other way
 * round; both sides check it before sending.
 */
public final class Network {
    public static final String VERSION = "1";
    /** Name of the channel. */
    public static final String CHANNEL = "main";

    private Network() {
    }

    /** The loader's payload registration; server handlers run on the server thread, client ones on the client thread. */
    public interface Registrar {
        <T extends Payloads.Payload> void toServer(Class<T> type, PayloadCodec<T> codec, BiConsumer<T, ServerPlayer> handler);

        <T extends Payloads.Payload> void toClient(Class<T> type, PayloadCodec<T> codec);
    }

    public static void register(Registrar registrar) {
        registrar.toServer(Payloads.OpenRequest.class, Payloads.OpenRequest.STREAM_CODEC, ServerRequests::open);
        registrar.toServer(Payloads.PlayersRequest.class, Payloads.PlayersRequest.STREAM_CODEC, ServerRequests::players);
        registrar.toServer(Payloads.TimelineRequest.class, Payloads.TimelineRequest.STREAM_CODEC, ServerRequests::timeline);
        registrar.toServer(Payloads.StateRequest.class, Payloads.StateRequest.STREAM_CODEC, ServerRequests::state);
        registrar.toServer(Payloads.ActionRequest.class, Payloads.ActionRequest.STREAM_CODEC, ServerRequests::action);
        registrar.toServer(Payloads.SearchRequest.class, Payloads.SearchRequest.STREAM_CODEC, ServerRequests::search);

        registrar.toClient(Payloads.Hello.class, Payloads.Hello.STREAM_CODEC);
        registrar.toClient(Payloads.Notice.class, Payloads.Notice.STREAM_CODEC);
        registrar.toClient(Payloads.PlayersResponse.class, Payloads.PlayersResponse.STREAM_CODEC);
        registrar.toClient(Payloads.TimelineResponse.class, Payloads.TimelineResponse.STREAM_CODEC);
        registrar.toClient(Payloads.StateResponse.class, Payloads.StateResponse.STREAM_CODEC);
        registrar.toClient(Payloads.SearchResponse.class, Payloads.SearchResponse.STREAM_CODEC);
    }
}
