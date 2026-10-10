package com.pockyl.inventorybackups;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config. Section and key names are kept from the 2.0 releases so existing config files keep working.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.translation(key("general")).push("general");
    }

    public static final ModConfigSpec.BooleanValue TICK_SAVE_ENABLED = BUILDER
            .comment("Save inventories periodically, every preservationPeriod seconds.")
            .translation(key("tickSaveEnabled"))
            .define("tickSaveEnabled", true);
    public static final ModConfigSpec.LongValue PRESERVATION_PERIOD = BUILDER
            .comment("Seconds between periodic saves. 600 = every 10 minutes. Values below 1 disable periodic saves.")
            .translation(key("preservationPeriod"))
            .defineInRange("preservationPeriod", 600, -1, Long.MAX_VALUE);
    public static final ModConfigSpec.BooleanValue DEAD_SAVE_ENABLED = BUILDER
            .comment("Save the inventory when a player dies, before the items drop.")
            .translation(key("deadSaveEnabled"))
            .define("deadSaveEnabled", true);
    public static final ModConfigSpec.BooleanValue JOIN_SAVE_ENABLED = BUILDER
            .comment("Save the inventory when a player joins the server.")
            .translation(key("joinSaveEnabled"))
            .define("joinSaveEnabled", true);
    public static final ModConfigSpec.BooleanValue QUIT_SAVE_ENABLED = BUILDER
            .comment("Save the inventory when a player leaves the server.")
            .translation(key("quitSaveEnabled"))
            .define("quitSaveEnabled", true);
    public static final ModConfigSpec.IntValue RETENTION_DAYS = BUILDER
            .comment("Backups older than this many days are deleted automatically (inventory and ender chest).")
            .translation(key("retentionDays"))
            .defineInRange("retentionDays", 7, 1, 365);

    static {
        BUILDER.pop();
        BUILDER.translation(key("enderchest")).push("enderchest");
    }

    public static final ModConfigSpec.BooleanValue ENDER_CHEST_ENABLED = BUILDER
            .comment("Master switch for ender chest backups and the /enderchest command.")
            .translation(key("enderChestEnabled"))
            .define("enderChestEnabled", true);
    public static final ModConfigSpec.BooleanValue ENDER_CHEST_TICK_SAVE_ENABLED = BUILDER
            .comment("Save ender chests periodically (uses preservationPeriod).")
            .translation(key("enderChestTickSaveEnabled"))
            .define("enderChestTickSaveEnabled", true);
    public static final ModConfigSpec.BooleanValue ENDER_CHEST_DEAD_SAVE_ENABLED = BUILDER
            .comment("Save the ender chest when a player dies.")
            .translation(key("enderChestDeadSaveEnabled"))
            .define("enderChestDeadSaveEnabled", true);
    public static final ModConfigSpec.BooleanValue ENDER_CHEST_JOIN_SAVE_ENABLED = BUILDER
            .comment("Save the ender chest when a player joins the server.")
            .translation(key("enderChestJoinSaveEnabled"))
            .define("enderChestJoinSaveEnabled", true);
    public static final ModConfigSpec.BooleanValue ENDER_CHEST_QUIT_SAVE_ENABLED = BUILDER
            .comment("Save the ender chest when a player leaves the server.")
            .translation(key("enderChestQuitSaveEnabled"))
            .define("enderChestQuitSaveEnabled", true);
    public static final ModConfigSpec.BooleanValue ENDER_CHEST_OPEN_SAVE_ENABLED = BUILDER
            .comment("Save the ender chest every time a player opens it.")
            .translation(key("enderChestOpenSaveEnabled"))
            .define("enderChestOpenSaveEnabled", false);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    private static String key(String name) {
        return InventoryBackups.MOD_ID + ".configuration." + name;
    }
}
