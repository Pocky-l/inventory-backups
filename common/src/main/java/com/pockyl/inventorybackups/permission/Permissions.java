package com.pockyl.inventorybackups.permission;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.platform.Platform;

import java.util.List;

/**
 * Permission nodes {@code inventorybackups.<name>}, resolved through the loader's permission system (NeoForge's
 * PermissionAPI, fabric-permissions-api on Fabric), so permission mods such as LuckPerms can grant them one by one.
 * Without a permission mod every node falls back to the operator level from the config (default 2).
 */
public final class Permissions {
    /** Browse timelines, preview states, take snapshots, see stats. */
    public static final Node VIEW = new Node("view");
    /** Restore states and undo restores, give copies of stored items. */
    public static final Node RESTORE = new Node("restore");
    /** Edit the live inventory and ender chest of online players. */
    public static final Node EDIT = new Node("edit");
    /** Search items and their transfers across all players. */
    public static final Node SEARCH = new Node("search");
    /** Open the admin screen of the client mod. */
    public static final Node UI = new Node("ui");

    public static final List<Node> ALL = List.of(VIEW, RESTORE, EDIT, SEARCH, UI);

    /** One permission; {@link #name()} is the part after {@code inventorybackups.}. */
    public record Node(String name) {
        public String nodeName() {
            return InventoryBackups.MOD_ID + "." + name;
        }

        public String titleKey() {
            return "inventorybackups.permission." + name;
        }

        public String descriptionKey() {
            return titleKey() + ".desc";
        }
    }

    private Permissions() {
    }

    /** The answer without a permission mod. */
    public static boolean byOperatorLevel(ServerPlayer player) {
        return atLevel(player.permissions(), Config.PERMISSION_LEVEL.get());
    }

    /** Whether the permissions include the operator level {@code level} (0-4). */
    public static boolean atLevel(PermissionSet permissions, int level) {
        return permissions.hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }

    public static boolean has(ServerPlayer player, Node node) {
        try {
            return Platform.INSTANCE.hasPermission(player, node);
        } catch (RuntimeException e) {
            // A broken or not yet initialized permission handler must not lock admins out.
            InventoryBackups.LOGGER.debug("Permission check for {} failed, using the operator level", node.nodeName(), e);
            return byOperatorLevel(player);
        }
    }

    /** Players are checked through the permission system; the console and command blocks by operator level. */
    public static boolean has(CommandSourceStack source, Node node) {
        ServerPlayer player = source.getPlayer();
        return player != null ? has(player, node) : atLevel(source.permissions(), Config.PERMISSION_LEVEL.get());
    }

    public static boolean hasAny(CommandSourceStack source) {
        for (Node node : ALL) {
            if (has(source, node)) {
                return true;
            }
        }
        return false;
    }
}
