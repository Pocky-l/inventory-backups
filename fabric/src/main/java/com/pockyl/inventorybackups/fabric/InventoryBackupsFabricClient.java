package com.pockyl.inventorybackups.fabric;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraftforge.fml.config.ModConfig;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.client.ClientConfig;
import com.pockyl.inventorybackups.client.ClientSession;
import com.pockyl.inventorybackups.client.InventoryBackupsClient;
import com.pockyl.inventorybackups.client.KeyBindings;

/**
 * The client part on Fabric: config, the key, the payload receivers.
 */
public final class InventoryBackupsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ForgeConfigRegistry.INSTANCE.register(InventoryBackups.MOD_ID, ModConfig.Type.CLIENT, ClientConfig.SPEC);
        InventoryBackupsClient.init();
        FabricNetwork.registerClient();
        KeyBindingHelper.registerKeyBinding(KeyBindings.OPEN);
        ClientTickEvents.END_CLIENT_TICK.register(client -> KeyBindings.onClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientSession.onLoggingOut());
    }
}
