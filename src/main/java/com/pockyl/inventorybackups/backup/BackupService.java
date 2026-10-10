package com.pockyl.inventorybackups.backup;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.storage.BackupStore;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Owns the storage of the running server: opened when the server starts, flushed and closed when it has stopped.
 */
public final class BackupService {
    @Nullable
    private static BackupStore store;
    @Nullable
    private static MinecraftServer server;

    private BackupService() {
    }

    static void start(MinecraftServer newServer) {
        stop();
        server = newServer;
        Path root = root(newServer);
        store = new BackupStore(root, newServer.registryAccess(), Config.KEYFRAME_INTERVAL::get);
        InventoryBackups.LOGGER.info("Inventory backups are stored in {}", root.toAbsolutePath().normalize());
        for (ModConfigSpec.ConfigValue<?> value : Config.DEPRECATED_VALUES) {
            if (!value.get().equals(value.getDefault())) {
                InventoryBackups.LOGGER.info("Config option {} is no longer used since 3.0 and is ignored",
                        String.join(".", value.getPath()));
            }
        }
    }

    static void stop() {
        if (store != null) {
            store.close();
            store = null;
        }
        server = null;
    }

    /** The storage of the running server, null while no server runs. */
    @Nullable
    public static BackupStore store() {
        return store;
    }

    public static Optional<BackupStore> optionalStore() {
        return Optional.ofNullable(store);
    }

    @Nullable
    public static MinecraftServer server() {
        return server;
    }

    private static Path root(MinecraftServer server) {
        String configured = Config.DIRECTORY.get().trim();
        if (configured.isEmpty()) {
            return server.getWorldPath(LevelResource.ROOT).resolve(InventoryBackups.MOD_ID).normalize();
        }
        return server.getServerDirectory().resolve(configured).normalize();
    }
}
