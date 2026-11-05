package com.pockyl.inventorybackups.backup;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.FlowDraft;
import com.pockyl.inventorybackups.storage.FlowType;


import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records items leaving and reaching players through the world: drops, death drops and pickups. Dropped item entities
 * are remembered for a while, so a pickup names the player who dropped the items: an exact link in the transfer trail.
 */
public final class FlowTracker {
    /** Item entities despawn after five minutes; remembering them a bit longer covers items kept alive by mods. */
    private static final long REMEMBER_MILLIS = 15 * 60_000L;
    private static final int REMEMBERED = 20_000;
    private static final Map<UUID, Dropper> DROPPED = new LinkedHashMap<>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Dropper> eldest) {
            return size() > REMEMBERED;
        }
    };

    private FlowTracker() {
    }

    private record Dropper(UUID player, String name, long time) {
    }

    /** A player threw items out (after other mods had their say). */
    public static void onToss(ServerPlayer player, ItemEntity entity) {
        record(player, entity, FlowType.TOSS);
    }

    /** The items a dead player drops, as they enter the world. */
    public static void onDrops(ServerPlayer player, Collection<ItemEntity> drops) {
        for (ItemEntity drop : drops) {
            record(player, drop, FlowType.DEATH_DROP);
        }
    }

    /**
     * A player picked up items.
     *
     * @param original the stack of the item entity before the pickup
     * @param left     what is left in the item entity after it
     */
    public static void onPickup(ServerPlayer player, ItemEntity entity, ItemStack original, ItemStack left) {
        int picked = original.getCount() - left.getCount();
        if (picked <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Dropper dropper = DROPPED.get(entity.getUUID());
        if (dropper != null && now - dropper.time() > REMEMBER_MILLIS) {
            dropper = null;
        }
        submit(List.of(new FlowDraft(now, FlowType.PICKUP, player.getUUID(), player.getScoreboardName(),
                original.copyWithCount(picked), dimension(player), entity.blockPosition(),
                dropper == null ? null : dropper.player(), dropper == null ? null : dropper.name(), null, null)));
    }

    public static void onServerStopped() {
        DROPPED.clear();
        ContainerSessions.clear();
    }

    private static void record(ServerPlayer player, ItemEntity entity, FlowType type) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        DROPPED.put(entity.getUUID(), new Dropper(player.getUUID(), player.getScoreboardName(), now));
        submit(List.of(new FlowDraft(now, type, player.getUUID(), player.getScoreboardName(), stack.copy(), dimension(player),
                entity.blockPosition(), null, null, null, null)));
    }

    static void submit(List<FlowDraft> drafts) {
        BackupStore store = BackupService.store();
        if (store != null && !drafts.isEmpty()) {
            store.submitFlows(new ArrayList<>(drafts));
        }
    }

    private static String dimension(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }
}
