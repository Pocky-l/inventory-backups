package com.pockyl.inventorybackups.permission;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;

import java.util.List;

/**
 * Permission nodes, resolved through NeoForge's PermissionAPI so permission mods such as LuckPerms can grant them one by
 * one. Without a permission mod every node falls back to the operator level from the config (default 2).
 */
@Mod.EventBusSubscriber(modid = InventoryBackups.MOD_ID)
public final class Permissions {
    /** Browse timelines, preview states, take snapshots, see stats. */
    public static final PermissionNode<Boolean> VIEW = node("view");
    /** Restore states and undo restores, give copies of stored items. */
    public static final PermissionNode<Boolean> RESTORE = node("restore");
    /** Edit the live inventory and ender chest of online players. */
    public static final PermissionNode<Boolean> EDIT = node("edit");
    /** Search items and their transfers across all players. */
    public static final PermissionNode<Boolean> SEARCH = node("search");
    /** Open the admin screen of the client mod. */
    public static final PermissionNode<Boolean> UI = node("ui");

    public static final List<PermissionNode<Boolean>> ALL = List.of(VIEW, RESTORE, EDIT, SEARCH, UI);

    private Permissions() {
    }

    private static PermissionNode<Boolean> node(String name) {
        PermissionNode<Boolean> node = new PermissionNode<>(InventoryBackups.MOD_ID, name, PermissionTypes.BOOLEAN,
                (player, uuid, context) -> player != null && player.hasPermissions(Config.PERMISSION_LEVEL.get()));
        node.setInformation(Component.translatable("inventorybackups.permission." + name),
                Component.translatable("inventorybackups.permission." + name + ".desc"));
        return node;
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(VIEW, RESTORE, EDIT, SEARCH, UI);
    }

    public static boolean has(ServerPlayer player, PermissionNode<Boolean> node) {
        try {
            return PermissionAPI.getPermission(player, node);
        } catch (RuntimeException e) {
            // A broken or not yet initialized permission handler must not lock admins out.
            InventoryBackups.LOGGER.debug("Permission check for {} failed, using the operator level", node.getNodeName(), e);
            return player.hasPermissions(Config.PERMISSION_LEVEL.get());
        }
    }

    /** Players are checked through the PermissionAPI; the console and command blocks by operator level. */
    public static boolean has(CommandSourceStack source, PermissionNode<Boolean> node) {
        ServerPlayer player = source.getPlayer();
        return player != null ? has(player, node) : source.hasPermission(Config.PERMISSION_LEVEL.get());
    }

    public static boolean hasAny(CommandSourceStack source) {
        for (PermissionNode<Boolean> node : ALL) {
            if (has(source, node)) {
                return true;
            }
        }
        return false;
    }
}
