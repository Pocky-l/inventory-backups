package com.pockyl.inventorybackups.storage;

import net.minecraft.nbt.CompoundTag;

/**
 * One timeline entry of a day log. A keyframe lists every non-empty slot; any other entry lists the slots that changed
 * since the previous entry, with pool index -1 for slots that became empty.
 *
 * @param time    epoch milliseconds, unique per player, also the id of the entry
 * @param changed number of slots that changed since the previous entry (also for keyframes)
 * @param refs    indices into the pool of the day log
 */
public record Entry(long time, Trigger trigger, EntryContext context, boolean keyframe, int changed, int[] slots, int[] refs,
                    int[] counts) {
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("t", time);
        tag.putByte("g", (byte) trigger.id());
        if (keyframe) {
            tag.putBoolean("k", true);
        }
        tag.putInt("c", changed);
        tag.putIntArray("s", slots);
        tag.putIntArray("r", refs);
        tag.putIntArray("n", counts);
        CompoundTag context = this.context.save();
        if (!context.isEmpty()) {
            tag.put("x", context);
        }
        return tag;
    }

    static Entry load(CompoundTag tag) {
        int[] slots = tag.getIntArray("s");
        int[] refs = tag.getIntArray("r");
        int[] counts = tag.getIntArray("n");
        if (refs.length != slots.length || counts.length != slots.length) {
            throw new IllegalStateException("Corrupt entry at " + tag.getLong("t"));
        }
        return new Entry(tag.getLong("t"), Trigger.byId(tag.getByte("g")), EntryContext.load(tag.getCompound("x")),
                tag.getBoolean("k"), tag.getInt("c"), slots, refs, counts);
    }
}
