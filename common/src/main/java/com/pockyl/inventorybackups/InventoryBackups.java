package com.pockyl.inventorybackups;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/**
 * Server-side backups of player inventories and ender chests. The mod adds no blocks or items, so it does not use the
 * shared Pocky Mods creative tab. The loader projects start it and forward the game events to
 * {@link com.pockyl.inventorybackups.event.CommonEvents}.
 */
public final class InventoryBackups {
    public static final String MOD_ID = "inventorybackups";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** The file name predates the workspace conventions; keeping it preserves the configs of existing servers. */
    public static final String CONFIG_FILE = "inventory/InventoryBackups.toml";

    private InventoryBackups() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
