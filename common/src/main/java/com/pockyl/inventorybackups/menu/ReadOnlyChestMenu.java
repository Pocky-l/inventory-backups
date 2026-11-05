package com.pockyl.inventorybackups.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A chest screen for looking at a backup: nothing can be taken out or put in.
 */
public final class ReadOnlyChestMenu extends ChestMenu {
    public ReadOnlyChestMenu(MenuType<?> type, int containerId, Inventory playerInventory, Container container, int rows) {
        super(type, containerId, playerInventory, container, rows);
    }

    @Override
    protected Slot addSlot(Slot slot) {
        // Called from the super constructor, before this class's own fields exist: only the slot itself is used.
        return super.addSlot(new LockedSlot(slot.container, slot.getContainerSlot(), slot.x, slot.y));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
