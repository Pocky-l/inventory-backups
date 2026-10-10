package com.pockyl.inventorybackups.storage;

/**
 * How items moved between a player and the world. The id is stored on disk, so ids must never change.
 */
public enum FlowType {
    /** The player dropped the items. */
    TOSS(0, "toss"),
    /** The items dropped when the player died. */
    DEATH_DROP(1, "death_drop"),
    /** The player picked the items up; the source names who dropped them, when known. */
    PICKUP(2, "pickup"),
    /** The player put the items into a container. */
    PUT(3, "put"),
    /** The player took the items out of a container. */
    TAKE(4, "take");

    private final int id;
    private final String key;

    FlowType(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() {
        return id;
    }

    public String key() {
        return key;
    }

    /** Whether the items left the player (as opposed to arriving). */
    public boolean outgoing() {
        return this == TOSS || this == DEATH_DROP || this == PUT;
    }

    public static FlowType byId(int id) {
        for (FlowType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return TOSS;
    }
}
