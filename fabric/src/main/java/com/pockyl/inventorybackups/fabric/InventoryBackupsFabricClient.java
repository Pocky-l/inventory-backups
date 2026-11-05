package com.pockyl.inventorybackups.fabric;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.client.ConfigScreenFactoryRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.client.ClientConfig;
import com.pockyl.inventorybackups.client.ClientSession;
import com.pockyl.inventorybackups.client.InventoryBackupsClient;
import com.pockyl.inventorybackups.client.KeyBindings;

/**
 * The client part on Fabric: config and config screen (shown by Mod Menu), the key, the payload receivers.
 */
public final class InventoryBackupsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ConfigRegistry.INSTANCE.register(InventoryBackups.MOD_ID, ModConfig.Type.CLIENT, ClientConfig.SPEC);
        ConfigScreenFactoryRegistry.INSTANCE.register(InventoryBackups.MOD_ID, ConfigurationScreen::new);
        InventoryBackupsClient.init();
        FabricNetwork.registerClient();
        KeyMappingHelper.registerKeyMapping(KeyBindings.OPEN);
        ClientTickEvents.END_CLIENT_TICK.register(client -> KeyBindings.onClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientSession.onLoggingOut());
    }
}
