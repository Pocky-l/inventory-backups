package com.pockyl.inventorybackups.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.backup.BackupKind;
import com.pockyl.inventorybackups.permission.Permissions;
import com.pockyl.inventorybackups.storage.EntryContext;

import static com.pockyl.inventorybackups.ui.Texts.tr;

/**
 * Tells the online admins in chat when a player dies with something in the inventory, with buttons to preview the
 * inventory from just before the death, restore it and teleport to the place (option {@code deathAlerts}). The player
 * who died is not told anything.
 */
public final class DeathAlerts {
    private DeathAlerts() {
    }

    /**
     * @param time the time of the entry recorded for the death, as the chat buttons name entries
     */
    public static void send(ServerPlayer dead, String cause, long time, EntryContext context) {
        if (!Config.DEATH_ALERTS.get() || !worthTelling(dead)) {
            return;
        }
        for (ServerPlayer admin : dead.server.getPlayerList().getPlayers()) {
            boolean view = Permissions.has(admin, Permissions.VIEW);
            boolean restore = Permissions.has(admin, Permissions.RESTORE);
            if (view || restore) {
                ChatUI.info(admin.createCommandSourceStack(), line(dead, cause, time, context, view, restore));
            }
        }
    }

    /** Whether the death loses anything: not with keepInventory, and an empty inventory has nothing to give back. */
    public static boolean worthTelling(ServerPlayer dead) {
        return !dead.getInventory().isEmpty() && !dead.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
    }

    /** The alert with the buttons the admin may use. */
    public static MutableComponent line(ServerPlayer dead, String cause, long time, EntryContext context, boolean view, boolean restore) {
        String args = " " + dead.getScoreboardName() + " " + time;
        MutableComponent line = Component.literal("☠ ").withStyle(ChatFormatting.RED)
                .append(tr("inventorybackups.alert.death", ChatUI.value(dead.getScoreboardName()),
                        Component.literal(cause).withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.GRAY));
        if (view) {
            line.append(" ").append(ChatUI.button("[👁]", ChatFormatting.GREEN, ChatUI.command(BackupKind.INVENTORY, "view" + args),
                    tr("inventorybackups.alert.hover.view")));
        }
        if (restore) {
            line.append(" ").append(ChatUI.button("[↻]", ChatFormatting.YELLOW, ChatUI.command(BackupKind.INVENTORY, "set" + args),
                    tr("inventorybackups.alert.hover.restore")));
        }
        if (view && !context.dimension().isEmpty()) {
            String position = ChatUI.position(context.pos());
            line.append(" ").append(ChatUI.button("[⌖]", ChatFormatting.DARK_AQUA,
                    "/execute in " + context.dimension() + " run tp @s " + position,
                    tr("inventorybackups.button.hover.teleport", context.dimension(), position)));
        }
        return line;
    }
}
