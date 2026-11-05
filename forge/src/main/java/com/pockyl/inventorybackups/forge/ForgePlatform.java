package com.pockyl.inventorybackups.forge;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.compat.SlotProvider;
import com.pockyl.inventorybackups.forge.compat.CosmeticArmorSlots;
import com.pockyl.inventorybackups.forge.compat.CuriosSlots;
import com.pockyl.inventorybackups.forge.compat.ToolBeltSlots;
import com.pockyl.inventorybackups.forge.compat.TravelersBackpackSlots;
import com.pockyl.inventorybackups.network.Payloads;
import com.pockyl.inventorybackups.permission.Permissions;
import com.pockyl.inventorybackups.platform.Platform;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link Platform} on Forge. Permissions go through the PermissionAPI, so LuckPerms and other permission mods can grant
 * each node; the default answer is the operator level from the config.
 */
public final class ForgePlatform implements Platform {
    static final Map<Permissions.Node, PermissionNode<Boolean>> NODES = new LinkedHashMap<>();

    static {
        for (Permissions.Node node : Permissions.ALL) {
            PermissionNode<Boolean> permission = new PermissionNode<>(InventoryBackups.MOD_ID, node.name(), PermissionTypes.BOOLEAN,
                    (player, uuid, context) -> player != null && Permissions.byOperatorLevel(player));
            permission.setInformation(Component.translatable(node.titleKey()), Component.translatable(node.descriptionKey()));
            NODES.put(node, permission);
        }
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean hasChannel(ServerPlayer player) {
        return ForgeNetwork.present(player.connection.connection);
    }

    @Override
    public void send(ServerPlayer player, Payloads.Payload payload) {
        ForgeNetwork.sendToPlayer(player, payload);
    }

    @Override
    public boolean hasPermission(ServerPlayer player, Permissions.Node node) {
        return PermissionAPI.getPermission(player, NODES.get(node));
    }

    @Override
    public ServerPlayer createFakePlayer(ServerLevel level, GameProfile profile) {
        return new FakePlayer(level, profile);
    }

    @Override
    public void playerSaved(ServerPlayer player, Path playerDataDir, String uuid) {
        ForgeEventFactory.firePlayerSavingEvent(player, playerDataDir.toFile(), uuid);
    }

    @Override
    public List<SlotProvider> slotProviders() {
        return List.of(new CuriosSlots(), new CosmeticArmorSlots(), new TravelersBackpackSlots(), new ToolBeltSlots());
    }
}
