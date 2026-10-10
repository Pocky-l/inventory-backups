package com.pockyl.inventorybackups.backup;

/**
 * The part of a state a chat command works on. The id names the command and the translation keys.
 */
public enum BackupKind {
    INVENTORY("inventory", 6),
    ENDER_CHEST("enderchest", 3);

    /** Chest slot of the first curio in the inventory preview; armor and offhand come right before it. */
    private static final int FIRST_CURIO_CHEST_SLOT = 41;

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
     * player screen: main inventory, hotbar, then helmet to boots, offhand and curios.
     */
    public int chestSlot(int slot) {
        int chest = switch (Slots.section(slot)) {
            case MAIN -> slot < 9 ? 27 + slot : slot - 9;
            case ARMOR -> 36 + Slots.ARMOR_START + Slots.ARMOR_SIZE - 1 - slot;
            case OFFHAND -> 40;
            case CURIOS -> FIRST_CURIO_CHEST_SLOT + slot - Slots.CURIOS_START;
            case ENDER_CHEST -> slot - Slots.ENDER_START;
            case UNKNOWN -> -1;
        };
        return covers(slot) && chest < previewRows * 9 ? chest : -1;
    }
}
