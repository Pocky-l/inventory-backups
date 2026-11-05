package com.pockyl.inventorybackups.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;

import com.pockyl.inventorybackups.event.CommonEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The items a player drops while dying, collected between the start and the end of the death loot and then reported
 * at once. Server thread only.
 */
public final class DeathDrops {
    private static final Map<UUID, List<ItemEntity>> COLLECTING = new HashMap<>();

    private DeathDrops() {
    }

    public static void start(ServerPlayer player) {
        COLLECTING.put(player.getUUID(), new ArrayList<>());
    }

    /** @return false when the player is not dying, so the drop is a toss */
    public static boolean collect(ServerPlayer player, ItemEntity entity) {
        List<ItemEntity> drops = COLLECTING.get(player.getUUID());
        if (drops == null) {
            return false;
        }
        drops.add(entity);
        return true;
    }

    public static void finish(ServerPlayer player) {
        List<ItemEntity> drops = COLLECTING.remove(player.getUUID());
        if (drops != null && !drops.isEmpty()) {
            CommonEvents.deathDrops(player, drops);
        }
    }
}
