package com.pockyl.inventorybackups.backup;

import com.mojang.authlib.GameProfile;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.menu.LivePlayerMenu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Players who are not online, loaded from their save file ({@code playerdata/<uuid>.dat}) so admins can restore and edit
 * them with the same code as online players. The loaded player is a {@link FakePlayer} that never enters the world;
 * other mods load their own data for it through the usual load event, so their slots work too.
 * <p>
 * Every change is written back right away, so the file is always current when the player logs in; the login then
 * closes the admin screens that still show the offline copy. All admins share one copy per player, so two admins
 * editing the same offline player never overwrite each other.
 */
@Mod.EventBusSubscriber(modid = InventoryBackups.MOD_ID)
public final class OfflinePlayers {
    /** Keys taken from the file as it was: the copy never moves, and it never rides what the player rode. */
    private static final String[] KEPT_KEYS = {"Pos", "Motion", "Rotation", "Dimension", "RootVehicle"};
    private static final Map<UUID, Handle> OPEN = new HashMap<>();

    private OfflinePlayers() {
    }

    private static final class Handle {
        final FakePlayer player;
        final CompoundTag loaded;
        int users;

        Handle(FakePlayer player, CompoundTag loaded) {
            this.player = player;
            this.loaded = loaded;
        }
    }

    /**
     * The offline copy of a player, loaded on first use; release it with {@link #release(ServerPlayer)}.
     *
     * @return empty when the player is online or has no save file
     */
    public static Optional<ServerPlayer> acquire(MinecraftServer server, UUID uuid, String name) {
        if (server.getPlayerList().getPlayer(uuid) != null) {
            return Optional.empty();
        }
        Handle handle = OPEN.get(uuid);
        if (handle == null) {
            handle = load(server, uuid, name);
            if (handle == null) {
                return Optional.empty();
            }
            OPEN.put(uuid, handle);
        }
        handle.users++;
        return Optional.of(handle.player);
    }

    public static void release(ServerPlayer player) {
        Handle handle = OPEN.get(player.getUUID());
        if (handle != null && handle.player == player && --handle.users <= 0) {
            OPEN.remove(player.getUUID());
        }
    }

    /** Whether the player is an offline copy loaded here. */
    public static boolean isOffline(ServerPlayer player) {
        Handle handle = OPEN.get(player.getUUID());
        return handle != null && handle.player == player;
    }

    /** Whether the copy may still be changed: false once the real player is online again. */
    public static boolean isValid(ServerPlayer player) {
        return isOffline(player) && player.server.getPlayerList().getPlayer(player.getUUID()) == null;
    }

    /** Whether the player has a save file to load. */
    public static boolean hasSaveFile(MinecraftServer server, UUID uuid) {
        return Files.isRegularFile(dataFile(server, uuid));
    }

    /** Writes an offline copy back to its save file; does nothing for online players. */
    public static void save(ServerPlayer player) {
        Handle handle = OPEN.get(player.getUUID());
        if (handle == null || handle.player != player || !isValid(player)) {
            return;
        }
        Path dir = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        String uuid = player.getStringUUID();
        try {
            CompoundTag tag = player.saveWithoutId(new CompoundTag());
            for (String key : KEPT_KEYS) {
                tag.remove(key);
                if (handle.loaded.contains(key)) {
                    tag.put(key, handle.loaded.get(key).copy());
                }
            }
            // The same steps as vanilla's player saving, so the old file stays as .dat_old and mods save their part.
            Path temp = Files.createTempFile(dir, uuid + "-", ".dat");
            NbtIo.writeCompressed(tag, temp.toFile());
            Util.safeReplaceFile(dir.resolve(uuid + ".dat").toFile(), temp.toFile(), dir.resolve(uuid + ".dat_old").toFile());
            ForgeEventFactory.firePlayerSavingEvent(player, dir.toFile(), uuid);
        } catch (IOException | RuntimeException e) {
            InventoryBackups.LOGGER.error("Could not save the offline player {}", player.getScoreboardName(), e);
        }
    }

    @Nullable
    private static Handle load(MinecraftServer server, UUID uuid, String name) {
        Path file = dataFile(server, uuid);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        // The copy is made in the player's dimension, so saving it keeps them where they logged out.
        ServerLevel level = server.overworld();
        try {
            CompoundTag raw = NbtIo.readCompressed(file.toFile());
            ResourceLocation dimension = ResourceLocation.tryParse(raw.getString("Dimension"));
            if (dimension != null) {
                ServerLevel found = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
                level = found != null ? found : level;
            }
        } catch (IOException | RuntimeException e) {
            InventoryBackups.LOGGER.error("Could not read the save file of {}", name, e);
            return null;
        }
        GameProfile profile = server.getProfileCache() == null ? null : server.getProfileCache().get(uuid).orElse(null);
        FakePlayer player = new FakePlayer(level, profile != null ? profile : new GameProfile(uuid, name));
        CompoundTag loaded = server.getPlayerList().load(player);
        if (loaded == null) {
            return null;
        }
        return new Handle(player, loaded);
    }

    private static Path dataFile(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat");
    }

    // Before the change tracker sees the login: admin screens on the offline copy close, the copy is dropped.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        UUID uuid = event.getEntity().getUUID();
        if (!OPEN.containsKey(uuid) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (ServerPlayer admin : player.server.getPlayerList().getPlayers()) {
            if (admin.containerMenu instanceof LivePlayerMenu menu && menu.target().getUUID().equals(uuid)) {
                admin.closeContainer();
            }
        }
        OPEN.remove(uuid);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        OPEN.clear();
    }
}
