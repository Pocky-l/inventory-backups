package com.pockyl.inventorybackups.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.gametest.GameTests;

/**
 * NeoForge entry point: the config and the payloads; the game events are forwarded by {@link NeoForgeEvents}.
 */
@Mod(InventoryBackups.MOD_ID)
public final class InventoryBackupsNeoForge {
    public InventoryBackupsNeoForge(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC, InventoryBackups.CONFIG_FILE);
        modBus.addListener(NeoForgeNetwork::register);
        GameTests.register(modBus);
    }
}
