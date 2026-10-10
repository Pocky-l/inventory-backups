package com.pockyl.inventorybackups.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Where and why an entry was recorded.
 *
 * @param dimension    dimension id of the player, empty when unknown (imports)
 * @param pos          block position of the player
 * @param detail       death message, container block or menu id, or an admin note
 * @param containerPos position of the container of a container session
 * @param actor        name of the admin for restores, edits and snapshots
 */
public record EntryContext(String dimension, BlockPos pos, @Nullable String detail, @Nullable BlockPos containerPos,
                           @Nullable String actor) {
    public static final EntryContext NONE = new EntryContext("", BlockPos.ZERO, null, null, null);

    public EntryContext withDetail(@Nullable String newDetail) {
        return new EntryContext(dimension, pos, newDetail, containerPos, actor);
    }

    public EntryContext withActor(@Nullable String newActor) {
        return new EntryContext(dimension, pos, detail, containerPos, newActor);
    }

    public EntryContext withContainer(@Nullable String block, @Nullable BlockPos at) {
        return new EntryContext(dimension, pos, block, at, actor);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (!dimension.isEmpty()) {
            tag.putString("d", dimension);
            tag.putIntArray("p", new int[] {pos.getX(), pos.getY(), pos.getZ()});
        }
        if (detail != null) {
            tag.putString("i", detail);
        }
        if (containerPos != null) {
            tag.putLong("c", containerPos.asLong());
        }
        if (actor != null) {
            tag.putString("a", actor);
        }
        return tag;
    }

    static EntryContext load(CompoundTag tag) {
        if (tag.isEmpty()) {
            return NONE;
        }
        BlockPos pos = BlockPos.ZERO;
        if (tag.get("p") instanceof IntArrayTag array && array.size() == 3) {
            int[] xyz = array.getAsIntArray();
            pos = new BlockPos(xyz[0], xyz[1], xyz[2]);
        }
        return new EntryContext(tag.getString("d"), pos,
                tag.contains("i", Tag.TAG_STRING) ? tag.getString("i") : null,
                tag.contains("c", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("c")) : null,
                tag.contains("a", Tag.TAG_STRING) ? tag.getString("a") : null);
    }
}
