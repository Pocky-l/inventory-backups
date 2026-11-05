package com.pockyl.inventorybackups.fabric;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.permission.Permissions;
import com.pockyl.inventorybackups.platform.Platform;

import java.nio.file.Path;

/**
 * {@link Platform} on Fabric. Permissions go through fabric-permissions-api (bundled), which LuckPerms and other
 * permission mods implement; without one it answers with the operator level from the config.
 */
public final class FabricPlatform implements Platform {
    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean hasChannel(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type);
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public boolean hasPermission(ServerPlayer player, Permissions.Node node) {
        return me.lucko.fabric.api.permissions.v0.Permissions.check(player, node.nodeName(), Config.PERMISSION_LEVEL.get());
    }

    @Override
    public ServerPlayer createFakePlayer(ServerLevel level, GameProfile profile) {
        // A new copy each time: FakePlayer.get would hand out one cached player per profile.
        return new FakePlayer(level, profile) {
        };
    }

    @Override
    public void playerLoaded(ServerPlayer player, MinecraftServer server) {
        // Fabric has no event for it; mods read their player data from the loaded tag itself.
    }

    @Override
    public void playerSaved(ServerPlayer player, Path playerDataDir, String uuid) {
        // Fabric has no event for it; mods keep their player data in the saved tag itself.
    }

    @Override
    public RegistryFriendlyByteBuf registryBuffer(ByteBuf buffer, RegistryAccess registries) {
        return new RegistryFriendlyByteBuf(buffer, registries);
    }
}
