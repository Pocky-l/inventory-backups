package com.pockyl.inventorybackups.backup;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.item.ItemStack;

/**
 * The part of a state a chat command works on. The id names the command and the translation keys.
 */
public enum BackupKind {
    INVENTORY("inventory", 6),
    ENDER_CHEST("enderchest", 3);

    /** Chest slot of the first mod slot in the inventory preview; armor and offhand come right before it. */
    private static final int FIRST_EXTRA_CHEST_SLOT = 41;

    private final String id;
    private final int previewRows;

    BackupKind(String id, int previewRows) {
        this.id = id;
        this.previewRows = previewRows;
    }

    public String id() {
        return id;
    }

    /** Rows of the chest screen used to preview or edit this kind of backup. */
    public int previewRows() {
        return previewRows;
    }

    public boolean covers(int slot) {
        return Slots.isEnderChest(slot) == (this == ENDER_CHEST);
    }

    /**
     * Position of a stored slot in the preview chest, or -1 when it is not shown. The inventory preview reads like the
     * player screen: main inventory, hotbar, then helmet to boots and offhand. The slots of other mods follow in the
     * remaining places in slot order, see {@link #preview(Int2ObjectMap)}, and get -1 here.
     */
    public int chestSlot(int slot) {
        int chest = switch (Slots.section(slot)) {
            case MAIN -> slot < 9 ? 27 + slot : slot - 9;
            case ARMOR -> 36 + Slots.ARMOR_START + Slots.ARMOR_SIZE - 1 - slot;
            case OFFHAND -> 40;
            case ENDER_CHEST -> slot - Slots.ENDER_START;
            case CURIOS, MOD, UNKNOWN -> -1;
        };
        return covers(slot) && chest < previewRows * 9 ? chest : -1;
    }

    /** The chest contents previewing a state; mod slots that do not fit into the last places are left out. */
    public Int2ObjectMap<ItemStack> preview(Int2ObjectMap<ItemStack> items) {
        Int2ObjectMap<ItemStack> chest = new Int2ObjectOpenHashMap<>();
        IntArrayList extras = new IntArrayList();
        items.int2ObjectEntrySet().forEach(slot -> {
            int place = chestSlot(slot.getIntKey());
            if (place >= 0) {
                chest.put(place, slot.getValue());
            } else if (this == INVENTORY && Slots.isExtra(slot.getIntKey())) {
                extras.add(slot.getIntKey());
            }
        });
        extras.sort(null);
        for (int i = 0; i < extras.size() && FIRST_EXTRA_CHEST_SLOT + i < previewRows * 9; i++) {
            chest.put(FIRST_EXTRA_CHEST_SLOT + i, items.get(extras.getInt(i)));
        }
        return chest;
    }
}
