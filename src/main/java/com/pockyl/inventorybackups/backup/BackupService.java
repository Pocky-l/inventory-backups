package com.pockyl.inventorybackups.backup;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.Config;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Takes backups of online players. Remembers the last backup per player so periodic saves can skip unchanged contents.
 */
public final class BackupService {
    public static final String DEATH = "death";
    public static final String JOIN = "join";
    public static final String QUIT = "quit";
    public static final String OPEN = "open";

    private static final Map<BackupKind, Map<UUID, BackupData>> LAST = new EnumMap<>(BackupKind.class);

    private BackupService() {
    }

    /**
     * Saves a backup unless the contents are empty, or unchanged since the last backup when {@code skipUnchanged}.
     *
     * @param suffix appended to the backup name to tell the trigger apart; null for periodic saves
     * @return the backup name, if one was written
     */
    public static Optional<String> save(BackupKind kind, ServerPlayer player, @Nullable String suffix, boolean skipUnchanged) {
        if (kind == BackupKind.ENDER_CHEST && !Config.ENDER_CHEST_ENABLED.get()) {
            return Optional.empty();
        }
        BackupData backup = BackupData.encode(player.registryAccess(), PlayerSlots.collect(kind, player));
        if (backup.size() == 0) {
            return Optional.empty();
        }
        Map<UUID, BackupData> last = LAST.computeIfAbsent(kind, k -> new HashMap<>());
        if (skipUnchanged && backup.equals(last.get(player.getUUID()))) {
            return Optional.empty();
        }
        last.put(player.getUUID(), backup);
        return BackupStorage.save(kind, player.getUUID(), backup, suffix);
    }

    /** Drops the remembered backups of a player who left. */
    public static void forget(UUID player) {
        LAST.values().forEach(last -> last.remove(player));
    }
}
