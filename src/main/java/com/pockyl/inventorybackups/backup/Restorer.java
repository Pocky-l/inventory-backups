package com.pockyl.inventorybackups.backup;

import it.unimi.dsi.fastutil.ints.IntCollection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.EntryContext;
import com.pockyl.inventorybackups.storage.SlotKeys;
import com.pockyl.inventorybackups.storage.StateView;
import com.pockyl.inventorybackups.storage.Trigger;

/**
 * Admin actions on a stored state. Every restore is framed by two entries: the state right before it (so it can be
 * undone) and a restore marker naming the admin and the restored entry.
 */
public final class Restorer {
    private Restorer() {
    }

    public static void restore(ServerPlayer target, StateView state, boolean inventory, boolean enderChest, String admin) {
        ChangeTracker.capture(target, Trigger.CHANGE, EntryContext.of(target));
        PlayerSlots.restore(target, state.items(), inventory, enderChest, slotKeys());
        record(target, state, admin);
        InventoryBackups.LOGGER.info("{} restored the {}{} of {} to the state of {}", admin, inventory ? "inventory" : "",
                enderChest ? (inventory ? " and ender chest" : "ender chest") : "", target.getScoreboardName(), state.entry().time());
    }

    public static void restoreSlots(ServerPlayer target, StateView state, IntCollection slots, String admin) {
        ChangeTracker.capture(target, Trigger.CHANGE, EntryContext.of(target));
        PlayerSlots.restoreSlots(target, state.items(), slots, slotKeys());
        record(target, state, admin);
        InventoryBackups.LOGGER.info("{} restored {} slots of {} to the state of {}", admin, slots.size(), target.getScoreboardName(),
                state.entry().time());
    }

    /** Gives copies of stacks to a player; what does not fit drops at their feet. */
    public static void give(ServerPlayer receiver, Iterable<ItemStack> stacks) {
        PlayerSlots.give(receiver, stacks);
    }

    @Nullable
    private static SlotKeys slotKeys() {
        BackupStore store = BackupService.store();
        return store == null ? null : store.slotKeys();
    }

    private static void record(ServerPlayer target, StateView state, String admin) {
        EntryContext context = EntryContext.of(target).withActor(admin).withDetail(Long.toString(state.entry().time()));
        ChangeTracker.capture(target, Trigger.RESTORE, context);
    }
}
