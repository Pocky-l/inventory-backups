package com.pockyl.inventorybackups.backup;

import java.util.Objects;

/**
 * One stored slot: the slot index (see {@link PlayerSlots}) and the item as SNBT. Field names are part of the backup
 * file format.
 */
public final class ItemData {
    private int index;
    private String nbt;

    public ItemData(int index, String nbt) {
        this.index = index;
        this.nbt = nbt;
    }

    public int index() {
        return index;
    }

    public String nbt() {
        return nbt;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemData other && index == other.index && Objects.equals(nbt, other.nbt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, nbt);
    }
}
