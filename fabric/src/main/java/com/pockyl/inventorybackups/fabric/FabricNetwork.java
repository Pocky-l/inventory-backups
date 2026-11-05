package com.pockyl.inventorybackups.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import com.pockyl.inventorybackups.network.ClientBridge;
import com.pockyl.inventorybackups.network.Network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The payloads on Fabric. A side without the mod simply does not have the channels; both sides check before sending.
 * Fabric API runs the handlers on the game thread.
 */
final class FabricNetwork {
    private static final List<CustomPacketPayload.Type<?>> TO_CLIENT = new ArrayList<>();

    private FabricNetwork() {
    }

    /** Both sides: the payload types and the server receivers. */
    static void register() {
        Network.register(new Network.Registrar() {
            @Override
            public <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                 BiConsumer<T, ServerPlayer> handler) {
                PayloadTypeRegistry.playC2S().register(type, codec);
                ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload, context.player()));
            }

            @Override
            public <T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
                PayloadTypeRegistry.playS2C().register(type, codec);
                TO_CLIENT.add(type);
            }
        });
    }

    /** Client side; only called on clients. */
    static void registerClient() {
        for (CustomPacketPayload.Type<?> type : TO_CLIENT) {
            receive(type);
        }
    }

    private static <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> ClientBridge.receive(payload));
    }
}
