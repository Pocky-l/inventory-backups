package com.pockyl.inventorybackups.platform;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.pockyl.inventorybackups.compat.SlotProvider;
import com.pockyl.inventorybackups.network.Payloads;
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

    /** Whether the player's client has the mod's channel (the client part of the mod). */
    boolean hasChannel(ServerPlayer player);

    void send(ServerPlayer player, Payloads.Payload payload);

    /**
     * Asks the loader's permission system; without a permission mod the answer is the operator level from the config.
     * May throw when the permission system is broken; {@link Permissions} falls back to the operator level then.
     */
    boolean hasPermission(ServerPlayer player, Permissions.Node node);

    /** A player that never joins the world, to load and save the data of an offline player. */
    ServerPlayer createFakePlayer(ServerLevel level, GameProfile profile);

    /** Tells other mods that a player's save file was written, so they save their own part of it. */
    void playerSaved(ServerPlayer player, Path playerDataDir, String uuid);

    /** Slot providers of mods that only exist for this loader and need its API types. */
    default List<SlotProvider> slotProviders() {
        return List.of();
    }

    static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, type.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + type.getName() + " for this mod loader"));
    }
}
