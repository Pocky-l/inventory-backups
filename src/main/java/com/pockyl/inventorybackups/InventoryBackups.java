package com.pockyl.inventorybackups;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

import com.pockyl.inventorybackups.network.Network;

/**
 * Server-side backups of player inventories and ender chests. The mod adds no blocks or items, so it does not use the
 * shared Pocky Mods creative tab.
 */
@Mod(InventoryBackups.MOD_ID)
public final class InventoryBackups {
    public static final String MOD_ID = "inventorybackups";
    public static final Logger LOGGER = LogUtils.getLogger();

    // The no-argument constructor keeps the jar loadable on NeoForge for 1.20.1 as well.
    public InventoryBackups() {
        // The file name predates the workspace conventions; keeping it preserves the configs of existing servers.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "inventory/InventoryBackups.toml");
        Network.register();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
