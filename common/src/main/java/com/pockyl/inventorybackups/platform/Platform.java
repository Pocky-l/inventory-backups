package com.pockyl.inventorybackups.platform;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.pockyl.inventorybackups.compat.SlotProvider;
import com.pockyl.inventorybackups.permission.Permissions;

import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

/**
 * What the common code needs from the mod loader on the server side. Each loader project provides one implementation,
 * listed in its {@code META-INF/services}.
 */
public interface Platform {
    Platform INSTANCE = load(Platform.class);

    boolean isModLoaded(String modId);

    /** Whether the player's client registered the payload type (has the client part of the mod). */
    boolean hasChannel(ServerPlayer player, CustomPacketPayload.Type<?> type);

    void send(ServerPlayer player, CustomPacketPayload payload);

    /**
     * Asks the loader's permission system; without a permission mod the answer is the operator level from the config.
     * May throw when the permission system is broken; {@link Permissions} falls back to the operator level then.
     */
    boolean hasPermission(ServerPlayer player, Permissions.Node node);

    /** A player that never joins the world, to load and save the data of an offline player. */
    ServerPlayer createFakePlayer(ServerLevel level, GameProfile profile);

    /** Tells other mods that a player's save file was written, so they save their own part of it. */
    void playerSaved(ServerPlayer player, Path playerDataDir, String uuid);

    /** A buffer like the ones payloads are written to, to measure their size. */
    RegistryFriendlyByteBuf registryBuffer(ByteBuf buffer, RegistryAccess registries);

    /** Slot providers of mods that only exist for this loader and need its API types. */
    default List<SlotProvider> slotProviders() {
        return List.of();
    }

    static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, type.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + type.getName() + " for this mod loader"));
    }
}
