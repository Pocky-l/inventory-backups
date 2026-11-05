package com.pockyl.inventorybackups.forge;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.pockyl.inventorybackups.client.ClientConfig;
import com.pockyl.inventorybackups.client.ClientSession;
import com.pockyl.inventorybackups.client.ConfigScreen;
import com.pockyl.inventorybackups.client.InventoryBackupsClient;
import com.pockyl.inventorybackups.client.KeyBindings;

/**
 * The client part on Forge: config and config screen, the key, client events. Only loaded on the client.
 */
final class ForgeClient {
    private ForgeClient() {
    }

    // Still part of the mod's construction, before the configs are loaded, and never seen by a dedicated server.
    static void init() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> ConfigScreen.root(parent)));
        InventoryBackupsClient.init();
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterKeyMappingsEvent event) -> event.register(KeyBindings.OPEN));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                KeyBindings.onClientTick();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientSession.onLoggingOut());
    }
}
