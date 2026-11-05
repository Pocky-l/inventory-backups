package com.pockyl.inventorybackups.backup;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.compat.ModSlots;
import com.pockyl.inventorybackups.index.ItemIndex;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.LegacyImporter;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Owns the storage of the running server: opened when the server starts, flushed and closed when it has stopped.
 */
public final class BackupService {
    /** Folder of the 2.x JSON backups, relative to the server folder. */
    private static final String LEGACY_FOLDER = "InventoryLog";

    @Nullable
    private static BackupStore store;
    @Nullable
    private static MinecraftServer server;
    @Nullable
    private static ItemIndex index;

    private BackupService() {
    }

    static void start(MinecraftServer newServer) {
        stop();
        server = newServer;
        Path root = root(newServer);
        store = new BackupStore(root, newServer.registryAccess(), Config.KEYFRAME_INTERVAL::get);
        InventoryBackups.LOGGER.info("Inventory backups are stored in {}", root.toAbsolutePath().normalize());
        if (ModSlots.any()) {
            InventoryBackups.LOGGER.info("Backing up the slots of {}", String.join(", ", ModSlots.installed()));
        }
        importLegacyBackups(store, newServer.getServerDirectory().resolve(LEGACY_FOLDER));
        ItemIndex newIndex = new ItemIndex(root.resolve("index.dat"), newServer.registryAccess());
        BackupStore opened = store;
        store.execute(() -> newIndex.loadOrRebuild(opened));
        store.addListener(newIndex);
        index = newIndex;
        for (ModConfigSpec.ConfigValue<?> value : Config.DEPRECATED_VALUES) {
            if (!value.get().equals(value.getDefault())) {
                InventoryBackups.LOGGER.info("Config option {} is no longer used since 3.0 and is ignored",
                        String.join(".", value.getPath()));
            }
        }
    }

    /** Queues the one-time import of 2.x backups; it runs before any capture of this session is written. */
    private static void importLegacyBackups(BackupStore target, Path legacy) {
        Config.LegacyAction action = Config.LEGACY_BACKUPS.get();
        target.execute(() -> {
            LegacyImporter.Result result = LegacyImporter.run(target, legacy, action);
            if (result != null) {
                InventoryBackups.LOGGER.info("Imported {} 2.x backups of {} players ({} unreadable files skipped)", result.files(),
                        result.players(), result.skippedFiles());
            }
        });
    }

    static void stop() {
        if (store != null) {
            store.close();
            store = null;
        }
        index = null;
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

    /** The item index; only to be used on the storage thread. */
    @Nullable
    public static ItemIndex index() {
        return index;
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
