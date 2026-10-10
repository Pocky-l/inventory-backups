package com.pockyl.inventorybackups;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Server-side backups of player inventories and ender chests. The mod adds no blocks or items, so it does not use the
 * shared Pocky Mods creative tab.
 */
@Mod(InventoryBackups.MOD_ID)
public final class InventoryBackups {
    public static final String MOD_ID = "inventorybackups";
    public static final Logger LOGGER = LogUtils.getLogger();

    public InventoryBackups(ModContainer container) {
        // The file name predates the workspace conventions; keeping it preserves the configs of existing servers.
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "inventory/InventoryBackups.toml");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
