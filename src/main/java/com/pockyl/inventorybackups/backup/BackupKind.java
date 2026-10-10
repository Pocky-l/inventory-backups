package com.pockyl.inventorybackups.backup;

/**
 * What a backup holds. The id names the storage directory, the command and the translation keys.
 */
public enum BackupKind {
    INVENTORY("inventory", 6),
    ENDER_CHEST("enderchest", 3);

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
}
