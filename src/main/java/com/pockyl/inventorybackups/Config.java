package com.pockyl.inventorybackups;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Common config, {@code config/inventory/InventoryBackups.toml}. The {@code general} and {@code enderchest} keys of the
 * 2.x releases are kept, so existing files stay valid; keys that lost their meaning in 3.0 are marked as deprecated
 * and ignored instead of being removed.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final String DEPRECATED = "Deprecated since 3.0 and ignored: ";

    public enum LegacyAction {
        KEEP, ARCHIVE, DELETE
    }

    static {
        BUILDER.translation(key("general")).push("general");
    }

    public static final ModConfigSpec.BooleanValue TICK_SAVE_ENABLED = BUILDER
            .comment("Record inventory changes as they happen (in 2.x: periodic saves).")
            .translation(key("tickSaveEnabled"))
            .define("tickSaveEnabled", true);
    public static final ModConfigSpec.LongValue PRESERVATION_PERIOD = BUILDER
            .comment(DEPRECATED + "changes are recorded as they happen, see capture.settleSeconds.")
            .translation(key("preservationPeriod"))
            .defineInRange("preservationPeriod", 600, -1, Long.MAX_VALUE);
    public static final ModConfigSpec.BooleanValue DEAD_SAVE_ENABLED = BUILDER
            .comment("Record the inventory when a player dies, before the items drop.")
            .translation(key("deadSaveEnabled"))
            .define("deadSaveEnabled", true);
    public static final ModConfigSpec.BooleanValue JOIN_SAVE_ENABLED = BUILDER
            .comment("Mark joins on the timeline.")
            .translation(key("joinSaveEnabled"))
            .define("joinSaveEnabled", true);
    public static final ModConfigSpec.BooleanValue QUIT_SAVE_ENABLED = BUILDER
            .comment("Mark quits on the timeline.")
            .translation(key("quitSaveEnabled"))
            .define("quitSaveEnabled", true);
    public static final ModConfigSpec.IntValue RETENTION_DAYS = BUILDER
            .comment("Days to keep one state per day. Older states are deleted, except deaths and admin actions (see retention).")
            .translation(key("retentionDays"))
            .defineInRange("retentionDays", 30, 1, 3650);

    static {
        BUILDER.pop();
        BUILDER.translation(key("enderchest")).push("enderchest");
    }

    public static final ModConfigSpec.BooleanValue ENDER_CHEST_ENABLED = BUILDER
            .comment("Back up ender chests and enable the /enderchest command.")
            .translation(key("enderChestEnabled"))
            .define("enderChestEnabled", true);
    public static final ModConfigSpec.BooleanValue ENDER_CHEST_TICK_SAVE_ENABLED = BUILDER
            .comment("Record ender chest changes as they happen. When off, the ender chest is only recorded on join.")
            .translation(key("enderChestTickSaveEnabled"))
            .define("enderChestTickSaveEnabled", true);
    public static final ModConfigSpec.BooleanValue ENDER_CHEST_DEAD_SAVE_ENABLED = BUILDER
            .comment(DEPRECATED + "every entry holds the inventory and the ender chest.")
            .translation(key("enderChestDeadSaveEnabled"))
            .define("enderChestDeadSaveEnabled", true);
    public static final ModConfigSpec.BooleanValue ENDER_CHEST_JOIN_SAVE_ENABLED = BUILDER
            .comment(DEPRECATED + "every entry holds the inventory and the ender chest.")
            .translation(key("enderChestJoinSaveEnabled"))
            .define("enderChestJoinSaveEnabled", true);
    public static final ModConfigSpec.BooleanValue ENDER_CHEST_QUIT_SAVE_ENABLED = BUILDER
            .comment(DEPRECATED + "every entry holds the inventory and the ender chest.")
            .translation(key("enderChestQuitSaveEnabled"))
            .define("enderChestQuitSaveEnabled", true);
    public static final ModConfigSpec.BooleanValue ENDER_CHEST_OPEN_SAVE_ENABLED = BUILDER
            .comment(DEPRECATED + "ender chest changes are recorded as they happen.")
            .translation(key("enderChestOpenSaveEnabled"))
            .define("enderChestOpenSaveEnabled", false);

    static {
        BUILDER.pop();
        BUILDER.translation(key("capture")).push("capture");
    }

    public static final ModConfigSpec.DoubleValue SETTLE_SECONDS = BUILDER
            .comment("Changes are recorded once the inventory has been still for this long, so moving items around makes one entry.")
            .translation(key("settleSeconds"))
            .defineInRange("settleSeconds", 2.5, 0.1, 60.0);
    public static final ModConfigSpec.DoubleValue MAX_DELAY_SECONDS = BUILDER
            .comment("Changes are recorded at the latest this long after the first one, even if the inventory keeps changing.",
                    "This is also the most a server crash can lose.")
            .translation(key("maxDelaySeconds"))
            .defineInRange("maxDelaySeconds", 30.0, 1.0, 600.0);
    public static final ModConfigSpec.DoubleValue SCAN_SECONDS = BUILDER
            .comment("Seconds between full comparisons of all slots, for changes no event reports (Curios slots, mods that edit",
                    "inventories directly). Cheap: a few dozen stack comparisons per player.")
            .translation(key("scanSeconds"))
            .defineInRange("scanSeconds", 5.0, 0.5, 300.0);
    public static final ModConfigSpec.BooleanValue CONTAINER_SESSIONS = BUILDER
            .comment("Record which items players put into and take out of chests and other containers.")
            .translation(key("containerSessions"))
            .define("containerSessions", true);

    static {
        BUILDER.pop();
        BUILDER.translation(key("storage")).push("storage");
    }

    public static final ModConfigSpec.ConfigValue<String> DIRECTORY = BUILDER
            .comment("Where backups are stored. Empty: the 'inventorybackups' folder of the world. Otherwise a path relative to",
                    "the server folder; worlds then share it, so use a different path per world.")
            .translation(key("directory"))
            .define("directory", "");
    public static final ModConfigSpec.IntValue KEYFRAME_INTERVAL = BUILDER
            .comment("A full state is stored every this many entries, the entries in between only store changed slots.")
            .translation(key("keyframeInterval"))
            .defineInRange("keyframeInterval", 64, 1, 10_000);

    static {
        BUILDER.pop();
        BUILDER.translation(key("retention")).push("retention");
    }

    public static final ModConfigSpec.IntValue KEEP_ALL_HOURS = BUILDER
            .comment("Every recorded change is kept for this many hours.")
            .translation(key("keepAllHours"))
            .defineInRange("keepAllHours", 24, 1, 24 * 365);
    public static final ModConfigSpec.IntValue HOURLY_DAYS = BUILDER
            .comment("After that, one state per hour is kept for this many days; then one per day until general.retentionDays.")
            .translation(key("hourlyDays"))
            .defineInRange("hourlyDays", 7, 1, 3650);
    public static final ModConfigSpec.IntValue IMPORTANT_DAYS = BUILDER
            .comment("Deaths and admin actions (restores, edits, snapshots) are kept for this many days.")
            .translation(key("importantDays"))
            .defineInRange("importantDays", 90, 1, 3650);
    public static final ModConfigSpec.IntValue PLAYER_BUDGET_MB = BUILDER
            .comment("Disk budget per player in megabytes; the oldest days are deleted first. 0 = no limit.")
            .translation(key("playerBudgetMb"))
            .defineInRange("playerBudgetMb", 64, 0, 1_000_000);
    public static final ModConfigSpec.IntValue TOTAL_BUDGET_MB = BUILDER
            .comment("Disk budget for all players in megabytes; the oldest days are deleted first. 0 = no limit.")
            .translation(key("totalBudgetMb"))
            .defineInRange("totalBudgetMb", 4096, 0, 10_000_000);
    public static final ModConfigSpec.IntValue CLEANUP_INTERVAL_MINUTES = BUILDER
            .comment("Minutes between retention passes. A pass also runs when the server starts.")
            .translation(key("cleanupIntervalMinutes"))
            .defineInRange("cleanupIntervalMinutes", 60, 5, 24 * 60);

    static {
        BUILDER.pop();
        BUILDER.translation(key("migration")).push("migration");
    }

    public static final ModConfigSpec.EnumValue<LegacyAction> LEGACY_BACKUPS = BUILDER
            .comment("What happens to the 2.x JSON backups in InventoryLog/ after they were imported once:",
                    "KEEP leaves them, ARCHIVE renames the folder to InventoryLog-2.x, DELETE removes them.")
            .translation(key("legacyBackups"))
            .defineEnum("legacyBackups", LegacyAction.ARCHIVE);

    static {
        BUILDER.pop();
        BUILDER.translation(key("permissions")).push("permissions");
    }

    public static final ModConfigSpec.IntValue PERMISSION_LEVEL = BUILDER
            .comment("Operator level needed for every feature when no permission mod decides (inventorybackups.* nodes).")
            .translation(key("permissionLevel"))
            .defineInRange("permissionLevel", 2, 0, 4);

    static {
        BUILDER.pop();
        BUILDER.translation(key("search")).push("search");
    }

    public static final ModConfigSpec.IntValue PROBABLE_LINK_SECONDS = BUILDER
            .comment("An item that leaves one player and shows up at another within this many seconds is shown as a probable",
                    "transfer when no toss, pickup or container explains it.")
            .translation(key("probableLinkSeconds"))
            .defineInRange("probableLinkSeconds", 15, 1, 600);
    public static final ModConfigSpec.IntValue PROBABLE_LINK_DISTANCE = BUILDER
            .comment("Players must have been at most this many blocks apart for a probable transfer.")
            .translation(key("probableLinkDistance"))
            .defineInRange("probableLinkDistance", 24, 1, 1024);
    public static final ModConfigSpec.IntValue MAX_RESULTS = BUILDER
            .comment("Most players, transfers and timeline entries returned by one search or request.")
            .translation(key("maxResults"))
            .defineInRange("maxResults", 500, 10, 10_000);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** Keys only kept so 2.x files stay valid; listed for the startup notice. */
    public static final List<ModConfigSpec.ConfigValue<?>> DEPRECATED_VALUES = List.of(PRESERVATION_PERIOD,
            ENDER_CHEST_DEAD_SAVE_ENABLED, ENDER_CHEST_JOIN_SAVE_ENABLED, ENDER_CHEST_QUIT_SAVE_ENABLED, ENDER_CHEST_OPEN_SAVE_ENABLED);

    private Config() {
    }

    // Capture timing runs on server ticks: a lagging server then waits as many game moments, and tests can rely on it.

    public static long settleTicks() {
        return Math.max(1, Math.round(SETTLE_SECONDS.get() * 20));
    }

    public static long maxDelayTicks() {
        return Math.max(1, Math.round(MAX_DELAY_SECONDS.get() * 20));
    }

    public static long scanTicks() {
        return Math.max(1, Math.round(SCAN_SECONDS.get() * 20));
    }

    private static String key(String name) {
        return InventoryBackups.MOD_ID + ".configuration." + name;
    }
}
