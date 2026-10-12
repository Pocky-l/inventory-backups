package com.pockyl.inventorybackups.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.pockyl.inventorybackups.backup.ChangeTracker;
import com.pockyl.inventorybackups.backup.OfflinePlayers;
import com.pockyl.inventorybackups.backup.PlayerSlots;
import com.pockyl.inventorybackups.compat.ModSlots;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A chest screen showing another player's live inventory or ender chest. Changes apply immediately, so nothing the
 * player does meanwhile is overwritten when the screen closes. The inventory screen shows the slots of other mods
 * (Curios, Accessories, ...) in the places after the offhand, filled ones first; chest slots beyond that stay locked.
 */
public final class LivePlayerMenu extends ChestMenu {
    private final ServerPlayer target;
    private final String admin;

    private LivePlayerMenu(MenuType<?> type, int containerId, Inventory playerInventory, Container contents, int rows,
                           ServerPlayer targetPlayer, String admin) {
        super(type, containerId, playerInventory, contents, rows);
        this.target = targetPlayer;
        this.admin = admin;
    }

    /** The player whose inventory is shown; an offline copy for offline players. */
    public ServerPlayer target() {
        return target;
    }

    /**
     * Opens the live inventory or ender chest of a player for an admin. While it is open the target's changes are held
     * back, so the whole edit is recorded as one entry naming the admin. The target may be an offline copy from
     * {@link OfflinePlayers#acquire}: the menu then owns that copy, writes every change to the save file and releases
     * the copy when it closes.
     */
    public static void open(ServerPlayer admin, ServerPlayer target, boolean enderChest, Component title) {
        int rows = enderChest ? 3 : 6;
        MenuType<?> type = enderChest ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x6;
        admin.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> {
            ChangeTracker.startEdit(target, admin.getScoreboardName());
            Container own = enderChest ? target.getEnderChestInventory() : target.getInventory();
            List<String> modSlots = enderChest ? List.of() : modSlots(target, rows * 9 - own.getContainerSize());
            Container contents = new PaddedContainer(own, rows * 9, target, modSlots);
            return new LivePlayerMenu(type, id, inventory, contents, rows, target, admin.getScoreboardName());
        }, title));
    }

    /** Keys of the mod slots shown, filled ones first, at most {@code places}. */
    private static List<String> modSlots(ServerPlayer target, int places) {
        List<String> filled = new ArrayList<>();
        List<String> empty = new ArrayList<>();
        for (Map.Entry<String, ItemStack> slot : ModSlots.read(target).entrySet()) {
            (slot.getValue().isEmpty() ? empty : filled).add(slot.getKey());
        }
        filled.addAll(empty);
        return filled.subList(0, Math.min(places, filled.size()));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        OfflinePlayers.save(target);
        ChangeTracker.endEdit(target, admin);
        OfflinePlayers.release(target);
    }

    @Override
    protected Slot addSlot(Slot slot) {
        // Called from the super constructor: only the slot itself is used.
        if (slot.container instanceof PaddedContainer padded && slot.getContainerSlot() >= padded.realSize()) {
            String key = padded.modSlot(slot.getContainerSlot());
            if (key != null) {
                return super.addSlot(new ModSlot(padded, slot.getContainerSlot(), slot.x, slot.y, key));
            }
            return super.addSlot(new LockedSlot(slot.container, slot.getContainerSlot(), slot.x, slot.y));
        }
        return super.addSlot(slot);
    }

    @Override
    public boolean stillValid(Player player) {
        if (OfflinePlayers.isOffline(target)) {
            return OfflinePlayers.isValid(target);
        }
        return !target.isRemoved() && !target.hasDisconnected();
    }

    /** A place showing a mod slot; it takes only what that mod would take. */
    private static final class ModSlot extends Slot {
        private final PaddedContainer padded;
        private final String key;

        private ModSlot(PaddedContainer container, int slot, int x, int y, String key) {
            super(container, slot, x, y);
            this.padded = container;
            this.key = key;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ModSlots.accepts(padded.target, key, stack);
        }
    }

    /**
     * Shows a smaller container in a bigger chest screen, followed by the given mod slots of the target; the places
     * after those are always empty.
     */
    private static final class PaddedContainer implements Container {
        private final Container delegate;
        private final int size;
        private final ServerPlayer target;
        private final List<String> modSlots;

        private PaddedContainer(Container delegate, int size, ServerPlayer target, List<String> modSlots) {
            this.delegate = delegate;
            this.size = size;
            this.target = target;
            this.modSlots = modSlots;
        }

        int realSize() {
            return Math.min(delegate.getContainerSize(), size);
        }

        /** The key of the mod slot at a place, or null. */
        String modSlot(int slot) {
            int index = slot - realSize();
            return index >= 0 && index < modSlots.size() ? modSlots.get(index) : null;
        }

        @Override
        public int getContainerSize() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            if (slot < realSize()) {
                return delegate.getItem(slot);
            }
            String key = modSlot(slot);
            return key == null ? ItemStack.EMPTY : ModSlots.get(target, key);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (slot < realSize()) {
                ItemStack taken = delegate.removeItem(slot, amount);
                OfflinePlayers.save(target);
                return taken;
            }
            String key = modSlot(slot);
            if (key == null) {
                return ItemStack.EMPTY;
            }
            // Mods notice their own setters more reliably than a stack shrinking in place.
            ItemStack rest = ModSlots.get(target, key).copy();
            ItemStack taken = rest.split(amount);
            boolean done = ModSlots.set(target, key, rest);
            OfflinePlayers.save(target);
            return done ? taken : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (slot < realSize()) {
                ItemStack taken = delegate.removeItemNoUpdate(slot);
                OfflinePlayers.save(target);
                return taken;
            }
            String key = modSlot(slot);
            if (key == null) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = ModSlots.get(target, key).copy();
            boolean done = ModSlots.set(target, key, ItemStack.EMPTY);
            OfflinePlayers.save(target);
            return done ? taken : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot < realSize()) {
                delegate.setItem(slot, stack);
                OfflinePlayers.save(target);
                return;
            }
            String key = modSlot(slot);
            if (key != null && !ModSlots.set(target, key, stack) && !stack.isEmpty()) {
                // The slot went away while the screen was open: the item goes to the player instead of vanishing.
                PlayerSlots.give(target, List.of(stack));
            }
            OfflinePlayers.save(target);
        }

        @Override
        public void setChanged() {
            delegate.setChanged();
            // An offline player's file is kept current after every change; for online players this does nothing.
            OfflinePlayers.save(target);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < realSize(); i++) {
                delegate.setItem(i, ItemStack.EMPTY);
            }
        }
    }
}
