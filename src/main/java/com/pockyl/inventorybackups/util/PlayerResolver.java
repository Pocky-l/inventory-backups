package com.pockyl.inventorybackups.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Finds a player by name among online players and, failing that, in the server's profile cache, so backups of offline
 * players can be browsed too.
 */
public final class PlayerResolver {
    private PlayerResolver() {
    }

    /**
     * @param online the player entity when they are online, otherwise null
     */
    public record ResolvedPlayer(UUID uuid, String name, @Nullable ServerPlayer online) {
        public boolean isOnline() {
            return online != null;
        }
    }

    public static Optional<ResolvedPlayer> resolve(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(new ResolvedPlayer(online.getUUID(), online.getScoreboardName(), online));
        }
        GameProfileCache cache = server.getProfileCache();
        if (cache == null) {
            return Optional.empty();
        }
        return cache.get(name).map(PlayerResolver::offline);
    }

    private static ResolvedPlayer offline(GameProfile profile) {
        return new ResolvedPlayer(profile.getId(), profile.getName(), null);
    }
}
