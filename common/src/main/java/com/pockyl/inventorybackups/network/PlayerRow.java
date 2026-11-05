package com.pockyl.inventorybackups.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * A player in the browser.
 *
 * @param lastDeath 0 when no death is stored
 */
public record PlayerRow(UUID uuid, String name, boolean online, long lastSeen, long lastDeath, long lastEntry) {
    public static PlayerRow read(FriendlyByteBuf buf) {
        return new PlayerRow(buf.readUUID(), NetCodecs.readString(buf), buf.readBoolean(), buf.readLong(), buf.readLong(), buf.readLong());
    }

    public static void write(FriendlyByteBuf buf, PlayerRow row) {
        buf.writeUUID(row.uuid);
        NetCodecs.writeString(buf, row.name);
        buf.writeBoolean(row.online);
        buf.writeLong(row.lastSeen);
        buf.writeLong(row.lastDeath);
        buf.writeLong(row.lastEntry);
    }
}
