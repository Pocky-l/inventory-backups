package com.pockyl.inventorybackups.storage;

import org.jetbrains.annotations.Nullable;

/**
 * Why a timeline entry was recorded. The id is stored on disk, so ids of existing values must never change.
 */
public enum Trigger {
    /** The inventory or ender chest changed and the changes settled. */
    CHANGE(0, "change", false, false),
    DEATH(1, "death", true, true),
    JOIN(2, "join", true, false),
    QUIT(3, "quit", true, false),
    /** A container session ended with changes; the context names the container. */
    CONTAINER(4, "container", false, false),
    /** An admin restored a state; the context names the admin. */
    RESTORE(5, "restore", true, true),
    /** An admin edited the live inventory; the context names the admin. */
    EDIT(6, "edit", false, true),
    /** An admin asked for a snapshot. */
    COMMAND(7, "command", true, true),
    /** Imported from a 2.x backup file. */
    IMPORT(8, "import", true, false);

    private static final Trigger[] BY_ID = new Trigger[9];

    static {
        for (Trigger trigger : values()) {
            BY_ID[trigger.id] = trigger;
        }
    }

    private final int id;
    private final String key;
    private final boolean recordUnchanged;
    private final boolean important;

    Trigger(int id, String key, boolean recordUnchanged, boolean important) {
        this.id = id;
        this.key = key;
        this.recordUnchanged = recordUnchanged;
        this.important = important;
    }

    public int id() {
        return id;
    }

    /** Lower-case name used in commands, filters and translation keys. */
    public String key() {
        return key;
    }

    /** Whether the entry is kept as a timeline marker even when nothing changed since the previous entry. */
    public boolean recordUnchanged() {
        return recordUnchanged;
    }

    /** Deaths and admin actions: kept longer by retention. */
    public boolean important() {
        return important;
    }

    public static Trigger byId(int id) {
        return id >= 0 && id < BY_ID.length && BY_ID[id] != null ? BY_ID[id] : CHANGE;
    }

    @Nullable
    public static Trigger byKey(String key) {
        for (Trigger trigger : values()) {
            if (trigger.key.equals(key)) {
                return trigger;
            }
        }
        return null;
    }
}
