package com.pockyl.inventorybackups.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Writes and reads one kind of payload. Minecraft 1.20.1 has no stream codecs; this keeps each payload's wire format next
 * to its fields, as they are on newer versions.
 */
public record PayloadCodec<T>(BiConsumer<FriendlyByteBuf, T> encoder, Function<FriendlyByteBuf, T> decoder) {
    public static <T> PayloadCodec<T> of(BiConsumer<FriendlyByteBuf, T> encoder, Function<FriendlyByteBuf, T> decoder) {
        return new PayloadCodec<>(encoder, decoder);
    }

    /** A payload without fields: nothing is written, the same instance is read. */
    public static <T> PayloadCodec<T> unit(T value) {
        return new PayloadCodec<>((buf, payload) -> {
        }, buf -> value);
    }

    public void encode(FriendlyByteBuf buf, T value) {
        encoder.accept(buf, value);
    }

    public T decode(FriendlyByteBuf buf) {
        return decoder.apply(buf);
    }
}
