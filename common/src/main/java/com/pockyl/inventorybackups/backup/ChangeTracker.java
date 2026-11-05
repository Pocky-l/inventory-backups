package com.pockyl.inventorybackups.backup;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.Capture;
import com.pockyl.inventorybackups.storage.EntryContext;
import com.pockyl.inventorybackups.storage.Trigger;
import com.pockyl.inventorybackups.ui.DeathAlerts;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records every inventory and ender chest change of online players, coalesced into one entry per burst, plus deaths,
 * joins, quits and container sessions as timeline markers.
 */
public final class ChangeTracker {
    private static final Map<UUID, TrackedPlayer> PLAYERS = new HashMap<>();

    private ChangeTracker() {
    }

    public static void onServerStarting(MinecraftServer server) {
        PLAYERS.clear();
        ContainerSessions.clear();
        BackupService.start(server);
    }

    public static void onServerStopped() {
        for (TrackedPlayer tracked : PLAYERS.values()) {
            submit(tracked.capture(Trigger.QUIT, EntryContext.of(tracked.player()), false));
        }
        PLAYERS.clear();
        BackupService.stop();
    }

    public static void onServerTick(MinecraftServer server) {
        if (PLAYERS.isEmpty()) {
            return;
        }
        long start = System.nanoTime();
        long now = server.getTickCount();
        long settle = Config.settleTicks();
        long maxDelay = Config.maxDelayTicks();
        long scan = Config.scanTicks();
        for (TrackedPlayer tracked : PLAYERS.values()) {
            Capture capture = tracked.tick(now, settle, maxDelay, scan);
            if (capture != null) {
                submit(capture);
            }
        }
        PerfStats.addTick(System.nanoTime() - start, PLAYERS.size());
    }

    public static void onJoin(ServerPlayer player) {
        long start = System.nanoTime();
        TrackedPlayer tracked = new TrackedPlayer(player);
        TrackedPlayer previous = PLAYERS.put(player.getUUID(), tracked);
        if (previous != null) {
            previous.unbind();
        }
        Trigger trigger = Config.JOIN_SAVE_ENABLED.get() ? Trigger.JOIN : Trigger.CHANGE;
        submit(tracked.capture(trigger, EntryContext.of(player), true));
        PerfStats.addEvent(System.nanoTime() - start);
    }

    public static void onQuit(ServerPlayer player) {
        TrackedPlayer tracked = PLAYERS.remove(player.getUUID());
        if (tracked == null) {
            return;
        }
        long start = System.nanoTime();
        Trigger trigger = Config.QUIT_SAVE_ENABLED.get() ? Trigger.QUIT : Trigger.CHANGE;
        submit(tracked.capture(trigger, EntryContext.of(player), false));
        tracked.unbind();
        PerfStats.addEvent(System.nanoTime() - start);
    }

    public static void onRespawn(ServerPlayer player) {
        TrackedPlayer tracked = PLAYERS.get(player.getUUID());
        if (tracked != null) {
            tracked.rebind(player);
        }
    }

    /** Before all other death handlers: mods may move items out of their slots on death (Traveler's Backpack, graves). */
    public static void beforeDeath(ServerPlayer player) {
        TrackedPlayer tracked = PLAYERS.get(player.getUUID());
        if (tracked != null) {
            long start = System.nanoTime();
            tracked.freeze();
            PerfStats.addEvent(System.nanoTime() - start);
        }
    }

    /** After all other death handlers and only for deaths no mod canceled; records the slots frozen before. */
    public static void onDeath(ServerPlayer player, DamageSource source) {
        TrackedPlayer tracked = PLAYERS.get(player.getUUID());
        if (tracked == null) {
            return;
        }
        long start = System.nanoTime();
        Trigger trigger = Config.DEAD_SAVE_ENABLED.get() ? Trigger.DEATH : Trigger.CHANGE;
        String cause = source.getLocalizedDeathMessage(player).getString();
        Capture capture = tracked.capture(trigger, EntryContext.of(player).withDetail(cause), false);
        submit(capture);
        DeathAlerts.send(player, cause, capture.time(), capture.context());
        PerfStats.addEvent(System.nanoTime() - start);
    }

    public static void onRightClickBlock(ServerPlayer player, BlockPos pos) {
        TrackedPlayer tracked = PLAYERS.get(player.getUUID());
        if (tracked != null) {
            tracked.rememberClick(pos, player.level().getGameTime());
        }
    }

    public static void onContainerOpen(ServerPlayer player, AbstractContainerMenu menu) {
        TrackedPlayer tracked = PLAYERS.get(player.getUUID());
        if (tracked == null) {
            return;
        }
        long start = System.nanoTime();
        tracked.watchMenu(menu);
        // Changes made before opening are recorded on their own, so the container entry only shows the session.
        submit(tracked.capture(Trigger.CHANGE, EntryContext.of(player), false));
        if (ContainerLocator.isInventoryScreen(menu)) {
            PerfStats.addEvent(System.nanoTime() - start);
            return;
        }
        ContainerSessions.open(player, menu, ContainerLocator.locate(player, menu, tracked.clickedBlock(), tracked.clickTick()));
        PerfStats.addEvent(System.nanoTime() - start);
    }

    public static void onContainerClose(ServerPlayer player, AbstractContainerMenu menu) {
        long start = System.nanoTime();
        ContainerLocator.Location location = ContainerSessions.close(player, menu, System.currentTimeMillis());
        TrackedPlayer tracked = PLAYERS.get(player.getUUID());
        if (tracked == null || location == null) {
            return;
        }
        EntryContext context = EntryContext.of(player).withContainer(location.id(), location.pos());
        submit(tracked.capture(Trigger.CONTAINER, context, false));
        PerfStats.addEvent(System.nanoTime() - start);
    }

    /**
     * Records the current state of an online player now, with the given trigger.
     *
     * @return false when the player is not tracked (offline or no server)
     */
    public static boolean capture(ServerPlayer player, Trigger trigger, EntryContext context) {
        TrackedPlayer tracked = PLAYERS.get(player.getUUID());
        if (tracked == null) {
            if (!OfflinePlayers.isOffline(player)) {
                return false;
            }
            // No running tracker for an offline copy: a full capture records its whole state, unchanged slots cost nothing.
            TrackedPlayer once = new TrackedPlayer(player);
            submit(once.capture(trigger, context, true));
            once.unbind();
            return true;
        }
        submit(tracked.capture(trigger, context, false));
        return true;
    }

    /** Holds the player's change entries while an admin edits the live inventory, so the edit becomes one entry. */
    public static void startEdit(ServerPlayer target, String admin) {
        TrackedPlayer tracked = PLAYERS.get(target.getUUID());
        if (tracked != null) {
            tracked.startEdit(admin);
        }
    }

    public static void endEdit(ServerPlayer target, String editor) {
        if (OfflinePlayers.isOffline(target)) {
            capture(target, Trigger.EDIT, EntryContext.of(target).withActor(editor));
            return;
        }
        TrackedPlayer tracked = PLAYERS.get(target.getUUID());
        if (tracked != null) {
            String admin = tracked.endEdit();
            submit(tracked.capture(Trigger.EDIT, EntryContext.of(target).withActor(admin), false));
        }
    }

    public static int trackedPlayers() {
        return PLAYERS.size();
    }

    @Nullable
    static TrackedPlayer tracked(UUID player) {
        return PLAYERS.get(player);
    }

    private static void submit(Capture capture) {
        BackupStore store = BackupService.store();
        // Most container openings and forced flushes find nothing new; skip them before they reach the storage thread.
        boolean nothing = capture.changes().isEmpty() && !capture.full() && !capture.trigger().recordUnchanged();
        if (store != null && !nothing) {
            store.submit(capture);
        }
    }
}
