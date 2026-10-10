package com.pockyl.inventorybackups.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.Consumer;

/**
 * Hands payloads for the client to the client part of the mod. Payload registration is common code, which must not
 * name client classes; the client part installs its receiver here when it loads. On a dedicated server nothing is
 * installed and nothing arrives.
 */
public final class ClientBridge {
    private static volatile Consumer<CustomPacketPayload> receiver = payload -> {
    };

    private ClientBridge() {
    }

    public static void install(Consumer<CustomPacketPayload> clientReceiver) {
        receiver = clientReceiver;
    }

    static void receive(CustomPacketPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> receiver.accept(payload));
    }
}
