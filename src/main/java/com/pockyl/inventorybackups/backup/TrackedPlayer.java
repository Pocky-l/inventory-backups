package com.pockyl.inventorybackups.backup;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.compat.ModSlots;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.Capture;
import com.pockyl.inventorybackups.storage.EntryContext;
import com.pockyl.inventorybackups.storage.SlotKeys;
import com.pockyl.inventorybackups.storage.Trigger;

/**
 * Change detection for one online player. Nothing is serialized per tick: vanilla already compares menu slots every tick
 * to sync them to the client, and the slot listener added here only raises a flag. The tracker records the changes
 * once the inventory has been still for the settle time, comparing against private copies of the last recorded stacks
 * and copying only the slots that differ.
 */
final class TrackedPlayer implements ContainerListener, net.minecraft.world.ContainerListener {
    private ServerPlayer player;
    /** Private copies of the last recorded stacks; handed to the storage thread and never modified afterwards. */
    private final Int2ObjectMap<ItemStack> last = new Int2ObjectOpenHashMap<>();
    private boolean dirty;
    private long firstDirty;
    private long lastDirty;
    private int timesChanged;
    private long nextScan;
    private int editors;
    @Nullable
    private String editor;
    @Nullable
    private BlockPos clickedBlock;
    private long clickTick;
    @Nullable
    private Int2ObjectMap<ItemStack> frozen;

    TrackedPlayer(ServerPlayer player) {
        this.player = player;
        // Spread the scans of different players over the interval instead of running them all in one tick.
        this.nextScan = player.server.getTickCount() + Math.floorMod(player.getUUID().hashCode(), 100);
        bind();
    }

    ServerPlayer player() {
        return player;
    }

    /** Attaches the listeners to the current player entity, which is replaced on respawn. */
    void rebind(ServerPlayer newPlayer) {
        unbind();
        player = newPlayer;
        bind();
        markDirty(player.server.getTickCount());
    }

    private void bind() {
        timesChanged = player.getInventory().getTimesChanged();
        player.inventoryMenu.addSlotListener(this);
        player.getEnderChestInventory().addListener(this);
    }

    void unbind() {
        player.inventoryMenu.removeSlotListener(this);
        player.getEnderChestInventory().removeListener(this);
    }

    void watchMenu(AbstractContainerMenu menu) {
        if (menu != player.inventoryMenu) {
            menu.addSlotListener(this);
        }
    }

    void rememberClick(BlockPos pos, long gameTime) {
        clickedBlock = pos.immutable();
        clickTick = gameTime;
    }

    @Nullable
    BlockPos clickedBlock() {
        return clickedBlock;
    }

    long clickTick() {
        return clickTick;
    }

    void startEdit(String admin) {
        editors++;
        editor = admin;
    }

    /** Ends a live edit and returns the admin who made it. */
    @Nullable
    String endEdit() {
        editors = Math.max(0, editors - 1);
        return editor;
    }

    @Override
    public void slotChanged(AbstractContainerMenu menu, int slotIndex, ItemStack stack) {
        long start = System.nanoTime();
        if (slotIndex >= 0 && slotIndex < menu.slots.size()) {
            Container container = menu.getSlot(slotIndex).container;
            if (container == player.getInventory()) {
                if (Config.TICK_SAVE_ENABLED.get()) {
                    markDirty(player.server.getTickCount());
                }
            } else if (container == player.getEnderChestInventory()) {
                enderChanged();
            }
        }
        PerfStats.addEvent(System.nanoTime() - start);
    }

    @Override
    public void dataChanged(AbstractContainerMenu menu, int dataSlotIndex, int value) {
    }

    @Override
    public void containerChanged(Container container) {
        long start = System.nanoTime();
        enderChanged();
        PerfStats.addEvent(System.nanoTime() - start);
    }

    private void enderChanged() {
        if (Config.ENDER_CHEST_ENABLED.get() && Config.ENDER_CHEST_TICK_SAVE_ENABLED.get()) {
            markDirty(player.server.getTickCount());
        }
    }

    void markDirty(long now) {
        if (!dirty) {
            firstDirty = now;
            dirty = true;
        }
        lastDirty = now;
    }

    /**
     * Per-tick check: a counter comparison, a full slot scan every few seconds, and a capture once the changes settled.
     *
     * @return the capture to store, or null
     */
    @Nullable
    Capture tick(long now, long settle, long maxDelay, long scan) {
        // Deaths happen inside the tick, so a state still frozen here belongs to a death that was canceled.
        frozen = null;
        int changed = player.getInventory().getTimesChanged();
        if (changed != timesChanged) {
            timesChanged = changed;
            if (Config.TICK_SAVE_ENABLED.get()) {
                markDirty(now);
            }
        }
        if (now >= nextScan) {
            nextScan = now + scan;
            if (!dirty && scanFindsChanges()) {
                markDirty(now);
            }
        }
        if (!dirty) {
            return null;
        }
        boolean overdue = now - firstDirty >= maxDelay;
        if (editors > 0) {
            return overdue ? capture(Trigger.EDIT, EntryContext.of(player).withActor(editor), false) : null;
        }
        if (overdue || now - lastDirty >= settle) {
            return capture(Trigger.CHANGE, EntryContext.of(player), false);
        }
        return null;
    }

    /**
     * Compares every slot with the last recorded copies. Catches what the listeners miss: the slots of other mods, which
     * have no change events, mods that edit inventories directly while another menu is open, and the inventory of a
     * player whose open menu has no player slots.
     */
    private boolean scanFindsChanges() {
        Inventory inventory = player.getInventory();
        if (Config.TICK_SAVE_ENABLED.get()) {
            for (int i = 0; i < Slots.MAIN_SIZE + Slots.ARMOR_SIZE + 1; i++) {
                if (differs(Slots.fromVanillaIndex(i), inventory.getItem(i))) {
                    return true;
                }
            }
        }
        if (Config.ENDER_CHEST_ENABLED.get() && Config.ENDER_CHEST_TICK_SAVE_ENABLED.get()) {
            SimpleContainer chest = player.getEnderChestInventory();
            for (int i = 0; i < Math.min(chest.getContainerSize(), Slots.ENDER_SIZE); i++) {
                if (differs(Slots.ENDER_START + i, chest.getItem(i))) {
                    return true;
                }
            }
        }
        return Config.TICK_SAVE_ENABLED.get() && modSlotsChanged();
    }

    private boolean differs(int slot, ItemStack current) {
        ItemStack previous = last.get(slot);
        return previous == null ? !current.isEmpty() : !ItemStack.matches(previous, current);
    }

    private boolean modSlotsChanged() {
        SlotKeys keys = slotKeys();
        if (keys == null || !ModSlots.any()) {
            return false;
        }
        Int2ObjectMap<ItemStack> mods = PlayerSlots.modSlots(player, keys);
        for (Int2ObjectMap.Entry<ItemStack> slot : mods.int2ObjectEntrySet()) {
            if (differs(slot.getIntKey(), slot.getValue())) {
                return true;
            }
        }
        for (int slot : last.keySet()) {
            if (Slots.isModSlot(slot) && !mods.containsKey(slot)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps copies of every slot as they are now, for the next capture. A death is recorded once it is certain (no mod
     * canceled it), but by then mods like Traveler's Backpack or grave mods may already have moved items out of their
     * slots; the death entry shows the slots as they were when the player died.
     */
    void freeze() {
        Int2ObjectMap<ItemStack> copies = new Int2ObjectOpenHashMap<>();
        PlayerSlots.live(player, Config.ENDER_CHEST_ENABLED.get(), slotKeys()).int2ObjectEntrySet()
                .forEach(e -> copies.put(e.getIntKey(), e.getValue().copy()));
        frozen = copies;
    }

    /**
     * Compares the live slots (or the frozen ones, see {@link #freeze()}) with the last recorded copies and returns the
     * differences.
     *
     * @param full whether the capture lists every slot (the first capture of a session)
     */
    Capture capture(Trigger trigger, EntryContext context, boolean full) {
        long start = System.nanoTime();
        dirty = false;
        boolean ender = Config.ENDER_CHEST_ENABLED.get();
        Int2ObjectMap<ItemStack> source = frozen;
        boolean copy = source == null;
        if (source == null) {
            source = PlayerSlots.live(player, ender, slotKeys());
        }
        frozen = null;
        Int2ObjectMap<ItemStack> changes = new Int2ObjectOpenHashMap<>();
        IntArrayList removed = new IntArrayList();
        for (int slot : last.keySet()) {
            // Without the ender chest in the capture its slots stay as they were last recorded.
            if (!source.containsKey(slot) && (ender || !Slots.isEnderChest(slot))) {
                removed.add(slot);
            }
        }
        removed.forEach(slot -> diff(changes, slot, ItemStack.EMPTY, false));
        for (Int2ObjectMap.Entry<ItemStack> slot : source.int2ObjectEntrySet()) {
            diff(changes, slot.getIntKey(), slot.getValue(), copy);
        }
        Int2ObjectMap<ItemStack> handed = full ? new Int2ObjectOpenHashMap<>(last) : changes;
        Capture capture = new Capture(player.getUUID(), player.getScoreboardName(), System.currentTimeMillis(), trigger, context,
                full, ender, handed);
        PerfStats.addCapture(System.nanoTime() - start);
        return capture;
    }

    @Nullable
    private static SlotKeys slotKeys() {
        BackupStore store = BackupService.store();
        return store == null ? null : store.slotKeys();
    }

    private void diff(Int2ObjectMap<ItemStack> changes, int slot, ItemStack current, boolean copy) {
        ItemStack previous = last.get(slot);
        if (previous == null ? current.isEmpty() : ItemStack.matches(previous, current)) {
            return;
        }
        if (current.isEmpty()) {
            last.remove(slot);
            changes.put(slot, ItemStack.EMPTY);
        } else {
            ItemStack stored = copy ? current.copy() : current;
            last.put(slot, stored);
            changes.put(slot, stored);
        }
    }
}
