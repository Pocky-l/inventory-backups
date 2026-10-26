package com.pockyl.inventorybackups.network;

import java.util.function.Consumer;

/**
 * Hands payloads for the client to the client part of the mod. Payload registration is common code, which must not
 * name client classes; the client part installs its receiver here when it loads. On a dedicated server nothing is
 * installed and nothing arrives.
 */
public final class ClientBridge {
    private static volatile Consumer<Payloads.Payload> receiver = payload -> {
    };

    private ClientBridge() {
    }

    public static void install(Consumer<Payloads.Payload> clientReceiver) {
        receiver = clientReceiver;
    }

    /** Called on the client thread. */
    static void receive(Payloads.Payload payload) {
        receiver.accept(payload);
    }
}
