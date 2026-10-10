package com.pockyl.inventorybackups.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.pockyl.inventorybackups.InventoryBackups;

/**
 * Registers the payloads. They are optional, so clients without the mod can join a server with it and the other way
 * round; both sides check {@code hasChannel} before sending.
 */
@EventBusSubscriber(modid = InventoryBackups.MOD_ID)
public final class Network {
    public static final String VERSION = "1";

    private Network() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION).optional();
        registrar.playToServer(Payloads.OpenRequest.TYPE, Payloads.OpenRequest.STREAM_CODEC, ServerRequests::open);
        registrar.playToServer(Payloads.PlayersRequest.TYPE, Payloads.PlayersRequest.STREAM_CODEC, ServerRequests::players);
        registrar.playToServer(Payloads.TimelineRequest.TYPE, Payloads.TimelineRequest.STREAM_CODEC, ServerRequests::timeline);
        registrar.playToServer(Payloads.StateRequest.TYPE, Payloads.StateRequest.STREAM_CODEC, ServerRequests::state);
        registrar.playToServer(Payloads.ActionRequest.TYPE, Payloads.ActionRequest.STREAM_CODEC, ServerRequests::action);
        registrar.playToServer(Payloads.SearchRequest.TYPE, Payloads.SearchRequest.STREAM_CODEC, ServerRequests::search);

        registrar.playToClient(Payloads.Hello.TYPE, Payloads.Hello.STREAM_CODEC, ClientBridge::receive);
        registrar.playToClient(Payloads.Notice.TYPE, Payloads.Notice.STREAM_CODEC, ClientBridge::receive);
        registrar.playToClient(Payloads.PlayersResponse.TYPE, Payloads.PlayersResponse.STREAM_CODEC, ClientBridge::receive);
        registrar.playToClient(Payloads.TimelineResponse.TYPE, Payloads.TimelineResponse.STREAM_CODEC, ClientBridge::receive);
        registrar.playToClient(Payloads.StateResponse.TYPE, Payloads.StateResponse.STREAM_CODEC, ClientBridge::receive);
        registrar.playToClient(Payloads.SearchResponse.TYPE, Payloads.SearchResponse.STREAM_CODEC, ClientBridge::receive);
    }
}
