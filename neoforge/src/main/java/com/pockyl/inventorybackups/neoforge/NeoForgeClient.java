package com.pockyl.inventorybackups.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.client.ClientConfig;
import com.pockyl.inventorybackups.client.ClientSession;
import com.pockyl.inventorybackups.client.InventoryBackupsClient;
import com.pockyl.inventorybackups.client.KeyBindings;

/**
 * The client part on NeoForge: config and config screen, the key, client events.
 */
@Mod(value = InventoryBackups.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClient {
    public NeoForgeClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        InventoryBackupsClient.init();
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(KeyBindings.OPEN));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> KeyBindings.onClientTick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientSession.onLoggingOut());
    }
}
