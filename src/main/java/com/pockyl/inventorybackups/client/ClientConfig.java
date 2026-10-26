package com.pockyl.inventorybackups.client;

import net.minecraftforge.common.ForgeConfigSpec;

import com.pockyl.inventorybackups.InventoryBackups;

/**
 * Client config, {@code config/inventorybackups-client.toml}: how the admin screen looks and behaves.
 */
public final class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public enum Zoom {
        HOUR, DAY, WEEK, ALL
    }

    static {
        BUILDER.translation(key("screen")).push("screen");
    }

    public static final ForgeConfigSpec.IntValue PLAY_SPEED = BUILDER
            .comment("Entries per second when the timeline plays.")
            .translation(key("playSpeed"))
            .defineInRange("playSpeed", 4, 1, 20);
    public static final ForgeConfigSpec.EnumValue<Zoom> DEFAULT_ZOOM = BUILDER
            .comment("Zoom of the timeline when a player is opened.")
            .translation(key("defaultZoom"))
            .defineEnum("defaultZoom", Zoom.DAY);
    public static final ForgeConfigSpec.BooleanValue CLOCK_24H = BUILDER
            .comment("Show times with a 24 hour clock.")
            .translation(key("clock24h"))
            .define("clock24h", true);
    public static final ForgeConfigSpec.BooleanValue HIGHLIGHT_CHANGES = BUILDER
            .comment("Color slots that changed compared with the previous entry (or pin A or B).")
            .translation(key("highlightChanges"))
            .define("highlightChanges", true);
    public static final ForgeConfigSpec.BooleanValue SLOT_HISTORY = BUILDER
            .comment("Show when and where an item got into its slot in item tooltips.")
            .translation(key("slotHistory"))
            .define("slotHistory", true);
    public static final ForgeConfigSpec.BooleanValue REMEMBER_PLAYER = BUILDER
            .comment("Reopen the screen on the player that was shown last.")
            .translation(key("rememberPlayer"))
            .define("rememberPlayer", true);

    static {
        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }

    private static String key(String name) {
        return InventoryBackups.MOD_ID + ".configuration." + name;
    }
}
