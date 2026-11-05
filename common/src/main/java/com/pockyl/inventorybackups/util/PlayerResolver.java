package com.pockyl.inventorybackups.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.backup.BackupService;
import com.pockyl.inventorybackups.storage.BackupStore;

import java.util.Optional;
import java.util.UUID;

/**
 * Finds a player by name (or UUID) among online players and, failing that, in the server's profile cache, so backups
 * of offline players can be browsed too.
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

    /** Resolves a player name or, for players without a known name, a UUID. */
    public static Optional<ResolvedPlayer> resolve(MinecraftServer server, String name) {
        UUID uuid = parseUuid(name);
        if (uuid != null) {
            ServerPlayer byUuid = server.getPlayerList().getPlayer(uuid);
            if (byUuid != null) {
                return Optional.of(new ResolvedPlayer(uuid, byUuid.getScoreboardName(), byUuid));
            }
            String known = server.services().nameToIdCache().get(uuid).map(NameAndId::name).orElse(null);
            return Optional.of(new ResolvedPlayer(uuid, known == null ? name : known, null));
        }
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(new ResolvedPlayer(online.getUUID(), online.getScoreboardName(), online));
        }
        Optional<ResolvedPlayer> cached = server.services().nameToIdCache().get(name).map(PlayerResolver::offline);
        if (cached.isPresent()) {
            return cached;
        }
        // Names the server forgot (the profile cache keeps only recent players) are still in the backups.
        BackupStore store = BackupService.store();
        return store == null ? Optional.empty()
                : store.playerByName(name).map(info -> new ResolvedPlayer(info.uuid(), info.name(), null));
    }

    @Nullable
    private static UUID parseUuid(String text) {
        if (text.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ResolvedPlayer offline(NameAndId profile) {
        return new ResolvedPlayer(profile.id(), profile.name(), null);
    }
}
