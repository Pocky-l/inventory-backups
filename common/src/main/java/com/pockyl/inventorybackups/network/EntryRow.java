package com.pockyl.inventorybackups.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.storage.Entry;
import com.pockyl.inventorybackups.storage.EntryContext;
import com.pockyl.inventorybackups.storage.Trigger;

/**
 * A timeline entry as sent to the client: everything but the stored slots.
 *
 * @param detail death message, container id or restored time; empty when none
 * @param actor  admin name; empty when none
 */
public record EntryRow(long time, Trigger trigger, int changed, String dimension, BlockPos pos, String detail, String actor,
                       @Nullable BlockPos containerPos) {
    public static EntryRow of(Entry entry) {
        EntryContext context = entry.context();
        return new EntryRow(entry.time(), entry.trigger(), entry.changed(), context.dimension(), context.pos(),
                context.detail() == null ? "" : context.detail(), context.actor() == null ? "" : context.actor(), context.containerPos());
    }

    public static EntryRow read(RegistryFriendlyByteBuf buf) {
        return new EntryRow(buf.readLong(), Trigger.byId(buf.readByte()), buf.readVarInt(), NetCodecs.readString(buf), buf.readBlockPos(),
                NetCodecs.readString(buf), NetCodecs.readString(buf), NetCodecs.readOptionalPos(buf));
    }

    public static void write(RegistryFriendlyByteBuf buf, EntryRow row) {
        buf.writeLong(row.time);
        buf.writeByte(row.trigger.id());
        buf.writeVarInt(row.changed);
        NetCodecs.writeString(buf, row.dimension);
        buf.writeBlockPos(row.pos);
        NetCodecs.writeString(buf, row.detail);
        NetCodecs.writeString(buf, row.actor);
        NetCodecs.writeOptionalPos(buf, row.containerPos);
    }
}
