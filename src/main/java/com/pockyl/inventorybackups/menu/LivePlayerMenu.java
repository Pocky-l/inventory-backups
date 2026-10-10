package com.pockyl.inventorybackups.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A chest screen showing another player's live inventory or ender chest. Changes apply immediately, so nothing the
 * player does meanwhile is overwritten when the screen closes. Chest slots beyond the real container stay locked.
 */
public final class LivePlayerMenu extends ChestMenu {
    private final ServerPlayer target;

    public LivePlayerMenu(MenuType<?> type, int containerId, Inventory playerInventory, Container target, int rows,
                          ServerPlayer targetPlayer) {
        super(type, containerId, playerInventory, new PaddedContainer(target, rows * 9), rows);
        this.target = targetPlayer;
    }

    @Override
    protected Slot addSlot(Slot slot) {
        // Called from the super constructor: only the slot itself is used.
        if (slot.container instanceof PaddedContainer padded && slot.getContainerSlot() >= padded.realSize()) {
            return super.addSlot(new LockedSlot(slot.container, slot.getContainerSlot(), slot.x, slot.y));
        }
        return super.addSlot(slot);
    }

    @Override
    public boolean stillValid(Player player) {
        return !target.isRemoved() && !target.hasDisconnected();
    }

    /** Shows a smaller container in a bigger chest screen; the extra slots are always empty. */
    private static final class PaddedContainer implements Container {
        private final Container delegate;
        private final int size;

        private PaddedContainer(Container delegate, int size) {
            this.delegate = delegate;
            this.size = size;
        }

        int realSize() {
            return Math.min(delegate.getContainerSize(), size);
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
            return slot < realSize() ? delegate.getItem(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return slot < realSize() ? delegate.removeItem(slot, amount) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return slot < realSize() ? delegate.removeItemNoUpdate(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot < realSize()) {
                delegate.setItem(slot, stack);
            }
        }

        @Override
        public void setChanged() {
            delegate.setChanged();
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
