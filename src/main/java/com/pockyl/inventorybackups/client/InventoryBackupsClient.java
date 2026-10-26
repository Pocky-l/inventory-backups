package com.pockyl.inventorybackups.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.network.ClientBridge;

/**
 * The optional client part: the admin screen, its key and its config. The server part works without it.
 */
@Mod.EventBusSubscriber(modid = InventoryBackups.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class InventoryBackupsClient {
    private InventoryBackupsClient() {
    }

    // Still part of the mod's construction, before the configs are loaded, and never seen by a dedicated server.
    @SubscribeEvent
    public static void onConstruct(FMLConstructModEvent event) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> ConfigScreen.root(parent)));
        ClientBridge.install(ClientSession::receive);
    }
}
