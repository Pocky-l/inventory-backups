package com.pockyl.inventorybackups.client;

import com.pockyl.inventorybackups.network.ClientBridge;

/**
 * The optional client part: the admin screen, its key and its config. The server part works without it. The loader
 * projects register {@link ClientConfig}, the key {@link KeyBindings#OPEN} and call {@link #init()}.
 */
public final class InventoryBackupsClient {
    private InventoryBackupsClient() {
    }

    public static void init() {
        ClientBridge.install(ClientSession::receive);
    }
}
