package com.pockyl.inventorybackups.neoforge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.pockyl.inventorybackups.network.ClientBridge;
import com.pockyl.inventorybackups.network.Network;

import java.util.function.BiConsumer;

/**
 * Registers the payloads as optional, so clients without the mod can join a server with it and the other way round.
 * NeoForge runs payload handlers on the game thread.
 */
final class NeoForgeNetwork {
    private NeoForgeNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Network.VERSION).optional();
        Network.register(new Network.Registrar() {
            @Override
            public <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                 BiConsumer<T, ServerPlayer> handler) {
                registrar.playToServer(type, codec, (payload, context) -> handler.accept(payload, (ServerPlayer) context.player()));
            }

            @Override
            public <T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
                registrar.playToClient(type, codec, (payload, context) -> ClientBridge.receive(payload));
            }
        });
    }
}
