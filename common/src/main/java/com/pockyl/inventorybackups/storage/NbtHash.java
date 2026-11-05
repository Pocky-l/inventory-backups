package com.pockyl.inventorybackups.storage;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
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
        if (tag instanceof CompoundTag compound) {
            List<String> keys = new ArrayList<>(compound.getAllKeys());
            keys.sort(null);
            hasher.putInt(keys.size());
            for (String key : keys) {
                hasher.putString(key, StandardCharsets.UTF_8);
                put(hasher, compound.get(key));
            }
        } else if (tag instanceof ListTag list) {
            hasher.putInt(list.size());
            for (Tag element : list) {
                put(hasher, element);
            }
        } else if (tag instanceof ByteArrayTag bytes) {
            hasher.putBytes(bytes.getAsByteArray());
        } else if (tag instanceof IntArrayTag ints) {
            for (int value : ints.getAsIntArray()) {
                hasher.putInt(value);
            }
        } else if (tag instanceof LongArrayTag longs) {
            for (long value : longs.getAsLongArray()) {
                hasher.putLong(value);
            }
        } else if (tag instanceof ByteTag value) {
            hasher.putByte(value.getAsByte());
        } else if (tag instanceof ShortTag value) {
            hasher.putShort(value.getAsShort());
        } else if (tag instanceof IntTag value) {
            hasher.putInt(value.getAsInt());
        } else if (tag instanceof LongTag value) {
            hasher.putLong(value.getAsLong());
        } else if (tag instanceof FloatTag value) {
            hasher.putFloat(value.getAsFloat());
        } else if (tag instanceof DoubleTag value) {
            hasher.putDouble(value.getAsDouble());
        } else {
            hasher.putString(tag.getAsString(), StandardCharsets.UTF_8);
        }
    }
}
