package com.pockyl.inventorybackups.network;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Reading and writing the parts payloads share. Every length read from the network is bounded, so a malicious client
 * cannot make the server allocate huge lists or strings.
 */
public final class NetCodecs {
    public static final int MAX_STRING = 256;
    public static final int MAX_LIST = 10_000;
    public static final int MAX_ITEMS = 512;

    private NetCodecs() {
    }

    public static String readString(FriendlyByteBuf buf) {
        return buf.readUtf(MAX_STRING);
    }

    public static void writeString(FriendlyByteBuf buf, @Nullable String value) {
        String text = value == null ? "" : value;
        buf.writeUtf(text.length() > MAX_STRING ? text.substring(0, MAX_STRING) : text, MAX_STRING);
    }

    public static <T> List<T> readList(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_LIST) {
            throw new IllegalArgumentException("List too long: " + size);
        }
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }

    public static <T> void writeList(FriendlyByteBuf buf, List<T> list, BiConsumer<FriendlyByteBuf, T> writer) {
        buf.writeVarInt(list.size());
        for (T element : list) {
            writer.accept(buf, element);
        }
    }

    public static Int2ObjectMap<ItemStack> readItems(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ITEMS) {
            throw new IllegalArgumentException("Too many items: " + size);
        }
        Int2ObjectMap<ItemStack> items = new Int2ObjectOpenHashMap<>(size);
        for (int i = 0; i < size; i++) {
            int slot = buf.readVarInt();
            items.put(slot, buf.readItem());
        }
        return items;
    }

    public static void writeItems(FriendlyByteBuf buf, Int2ObjectMap<ItemStack> items) {
        buf.writeVarInt(items.size());
        for (Int2ObjectMap.Entry<ItemStack> entry : items.int2ObjectEntrySet()) {
            buf.writeVarInt(entry.getIntKey());
            buf.writeItem(entry.getValue());
        }
    }

    @Nullable
    public static UUID readOptionalUuid(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUUID() : null;
    }

    public static void writeOptionalUuid(FriendlyByteBuf buf, @Nullable UUID uuid) {
        buf.writeBoolean(uuid != null);
        if (uuid != null) {
            buf.writeUUID(uuid);
        }
    }

    @Nullable
    public static BlockPos readOptionalPos(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readBlockPos() : null;
    }

    public static void writeOptionalPos(FriendlyByteBuf buf, @Nullable BlockPos pos) {
        buf.writeBoolean(pos != null);
        if (pos != null) {
            buf.writeBlockPos(pos);
        }
    }
}
