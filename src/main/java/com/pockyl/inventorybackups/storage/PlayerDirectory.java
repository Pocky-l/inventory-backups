package com.pockyl.inventorybackups.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.InventoryBackups;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Name, last activity and last death of every player with backups, so the player browser and searches can show offline
 * players without reading their files. Owned by the storage thread; saved to {@code players.dat}.
 */
final class PlayerDirectory {
    private final Path file;
    private final Map<UUID, PlayerInfo> players = new HashMap<>();
    private boolean dirty;

    PlayerDirectory(Path file) {
        this.file = file;
    }

    void load() {
        players.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            CompoundTag root = LogFile.readCompound(file);
            for (Tag tag : root.getList("players", Tag.TAG_COMPOUND)) {
                CompoundTag player = (CompoundTag) tag;
                UUID uuid = player.getUUID("u");
                players.put(uuid, new PlayerInfo(uuid, player.getString("n"), player.getLong("s"), player.getLong("d"),
                        player.getLong("e")));
            }
        } catch (IOException | RuntimeException e) {
            InventoryBackups.LOGGER.error("Could not read {}, it is rebuilt as players are seen", file, e);
        }
    }

    void save() {
        if (!dirty) {
            return;
        }
        ListTag list = new ListTag();
        for (PlayerInfo info : players.values()) {
            CompoundTag player = new CompoundTag();
            player.putUUID("u", info.uuid());
            player.putString("n", info.name());
            player.putLong("s", info.lastSeen());
            player.putLong("d", info.lastDeath());
            player.putLong("e", info.lastEntry());
            list.add(player);
        }
        CompoundTag root = new CompoundTag();
        root.put("players", list);
        try {
            LogFile.writeCompound(file, root);
            dirty = false;
        } catch (IOException e) {
            InventoryBackups.LOGGER.error("Could not save {}", file, e);
        }
    }

    void record(UUID uuid, String name, long time, Trigger trigger) {
        PlayerInfo old = players.get(uuid);
        String newName = name.isEmpty() && old != null ? old.name() : name;
        long lastDeath = trigger == Trigger.DEATH ? time : old == null ? 0 : old.lastDeath();
        long lastSeen = trigger == Trigger.IMPORT && old != null ? old.lastSeen() : Math.max(time, old == null ? 0 : old.lastSeen());
        players.put(uuid, new PlayerInfo(uuid, newName, lastSeen, lastDeath, Math.max(time, old == null ? 0 : old.lastEntry())));
        dirty = true;
    }

    void forget(UUID uuid) {
        if (players.remove(uuid) != null) {
            dirty = true;
        }
    }

    List<PlayerInfo> all() {
        return new ArrayList<>(players.values());
    }

    @Nullable
    PlayerInfo get(UUID uuid) {
        return players.get(uuid);
    }
}
