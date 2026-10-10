package com.pockyl.inventorybackups.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.network.ClientBridge;

/**
 * The optional client part: the admin screen, its key and its config. The server part works without it.
 */
@Mod(value = InventoryBackups.MOD_ID, dist = Dist.CLIENT)
public final class InventoryBackupsClient {
    public InventoryBackupsClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        ClientBridge.install(ClientSession::receive);
    }
}
