package com.pockyl.inventorybackups.backup;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.FlowDraft;
import com.pockyl.inventorybackups.storage.FlowType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records items leaving and reaching players through the world: drops, death drops and pickups. Dropped item entities
 * are remembered for a while, so a pickup names the player who dropped the items: an exact link in the transfer trail.
 */
@EventBusSubscriber(modid = InventoryBackups.MOD_ID)
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            record(player, event.getEntity(), FlowType.TOSS);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (ItemEntity drop : event.getDrops()) {
                record(player, drop, FlowType.DEATH_DROP);
            }
        }
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        int picked = event.getOriginalStack().getCount() - event.getCurrentStack().getCount();
        if (picked <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Dropper dropper = DROPPED.get(event.getItemEntity().getUUID());
        if (dropper != null && now - dropper.time() > REMEMBER_MILLIS) {
            dropper = null;
        }
        submit(List.of(new FlowDraft(now, FlowType.PICKUP, player.getUUID(), player.getScoreboardName(),
                event.getOriginalStack().copyWithCount(picked), dimension(player), event.getItemEntity().blockPosition(),
                dropper == null ? null : dropper.player(), dropper == null ? null : dropper.name(), null, null)));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
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
        return player.level().dimension().location().toString();
    }
}
