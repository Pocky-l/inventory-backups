package com.pockyl.inventorybackups.neoforge;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.compat.SlotProvider;
import com.pockyl.inventorybackups.neoforge.compat.CosmeticArmorSlots;
import com.pockyl.inventorybackups.permission.Permissions;
import com.pockyl.inventorybackups.platform.Platform;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link Platform} on NeoForge. Permissions go through the PermissionAPI, so LuckPerms and other permission mods can
 * grant each node; the default answer is the operator level from the config.
 */
public final class NeoForgePlatform implements Platform {
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
    public boolean hasChannel(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection.hasChannel(type);
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
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
    public void playerLoaded(ServerPlayer player, MinecraftServer server) {
        EventHooks.firePlayerLoadingEvent(player, server.getPlayerList(), player.getStringUUID());
    }

    @Override
    public void playerSaved(ServerPlayer player, Path playerDataDir, String uuid) {
        EventHooks.firePlayerSavingEvent(player, playerDataDir.toFile(), uuid);
    }

    @Override
    public RegistryFriendlyByteBuf registryBuffer(ByteBuf buffer, RegistryAccess registries) {
        return new RegistryFriendlyByteBuf(buffer, registries, ConnectionType.NEOFORGE);
    }

    @Override
    public List<SlotProvider> slotProviders() {
        return List.of(new CosmeticArmorSlots());
    }
}
