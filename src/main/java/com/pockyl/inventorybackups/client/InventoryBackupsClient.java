package com.pockyl.inventorybackups.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import com.pockyl.inventorybackups.InventoryBackups;

@Mod(value = InventoryBackups.MOD_ID, dist = Dist.CLIENT)
public final class InventoryBackupsClient {
    public InventoryBackupsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
