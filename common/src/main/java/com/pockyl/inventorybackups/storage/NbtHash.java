package com.pockyl.inventorybackups.storage;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A 64-bit content hash of an NBT tree. Compound keys are hashed in sorted order: {@link CompoundTag} iterates a hash map,
 * so two equal items built in a different order would otherwise hash differently and be stored twice.
 */
public final class NbtHash {
    private NbtHash() {
    }

    public static long hash(Tag tag) {
        Hasher hasher = Hashing.murmur3_128().newHasher();
        put(hasher, tag);
        return hasher.hash().asLong();
    }

    private static void put(Hasher hasher, Tag tag) {
        hasher.putByte(tag.getId());
        switch (tag) {
            case CompoundTag compound -> {
                List<String> keys = new ArrayList<>(compound.keySet());
                keys.sort(null);
                hasher.putInt(keys.size());
                for (String key : keys) {
                    hasher.putString(key, StandardCharsets.UTF_8);
                    put(hasher, compound.get(key));
                }
            }
            case ListTag list -> {
                hasher.putInt(list.size());
                for (Tag element : list) {
                    put(hasher, element);
                }
            }
            case ByteArrayTag bytes -> hasher.putBytes(bytes.getAsByteArray());
            case IntArrayTag ints -> {
                for (int value : ints.getAsIntArray()) {
                    hasher.putInt(value);
                }
            }
            case LongArrayTag longs -> {
                for (long value : longs.getAsLongArray()) {
                    hasher.putLong(value);
                }
            }
            case ByteTag value -> hasher.putByte(value.byteValue());
            case ShortTag value -> hasher.putShort(value.shortValue());
            case IntTag value -> hasher.putInt(value.intValue());
            case LongTag value -> hasher.putLong(value.longValue());
            case FloatTag value -> hasher.putFloat(value.floatValue());
            case DoubleTag value -> hasher.putDouble(value.doubleValue());
            case StringTag value -> hasher.putString(value.value(), StandardCharsets.UTF_8);
            default -> hasher.putString(tag.toString(), StandardCharsets.UTF_8);
        }
    }
}
