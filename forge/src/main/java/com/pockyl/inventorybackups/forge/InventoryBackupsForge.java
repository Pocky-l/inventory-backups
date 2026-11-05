package com.pockyl.inventorybackups.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;

/**
 * Forge entry point: the config and the payloads; the game events are forwarded by {@link ForgeEvents}.
 */
@Mod(InventoryBackups.MOD_ID)
public final class InventoryBackupsForge {
    // The no-argument constructor keeps the jar loadable on NeoForge for 1.20.1 as well.
    public InventoryBackupsForge() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, InventoryBackups.CONFIG_FILE);
        ForgeNetwork.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.init();
        }
    }
}
