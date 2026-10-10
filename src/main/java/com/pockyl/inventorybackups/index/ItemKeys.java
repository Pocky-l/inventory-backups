package com.pockyl.inventorybackups.index;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.storage.NbtHash;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What the index knows about a stored item, read straight from its NBT without decoding the stack: its id and, for
 * distinctive stacks (renamed, enchanted or otherwise carrying components), a fingerprint that follows one specific
 * item. Damage and repair cost are left out of the fingerprint, so a sword keeps its fingerprint while it wears down.
 *
 * @param fingerprint 0 for plain stacks
 * @param name        custom name in lower case, null when the stack has none
 */
public record ItemKeys(String itemId, long fingerprint, @Nullable String name, List<String> enchantments) {
    private static final Set<String> IGNORED = Set.of("minecraft:damage", "minecraft:repair_cost");
    public static final String ITEM_PREFIX = "i:";
    public static final String FINGERPRINT_PREFIX = "f:";

    public static ItemKeys of(CompoundTag tag, HolderLookup.Provider registries) {
        String id = tag.getString("id");
        CompoundTag components = tag.getCompound("components");
        CompoundTag distinctive = new CompoundTag();
        for (String key : components.getAllKeys()) {
            if (!IGNORED.contains(key)) {
                distinctive.put(key, components.get(key));
            }
        }
        long fingerprint = 0;
        if (!distinctive.isEmpty()) {
            CompoundTag identity = new CompoundTag();
            identity.putString("id", id);
            identity.put("components", distinctive);
            fingerprint = NbtHash.hash(identity);
            // 0 means "plain stack"; a real hash of 0 is astronomically unlikely but must not collide with it.
            if (fingerprint == 0) {
                fingerprint = 1;
            }
        }
        List<String> enchantments = new ArrayList<>();
        enchantments(components.getCompound("minecraft:enchantments"), enchantments);
        enchantments(components.getCompound("minecraft:stored_enchantments"), enchantments);
        return new ItemKeys(id, fingerprint, name(components, registries), List.copyOf(enchantments));
    }

    public String itemKey() {
        return ITEM_PREFIX + itemId;
    }

    @Nullable
    public String fingerprintKey() {
        return fingerprint == 0 ? null : fingerprintKey(fingerprint);
    }

    public static String fingerprintKey(long fingerprint) {
        return FINGERPRINT_PREFIX + Long.toHexString(fingerprint);
    }

    @Nullable
    private static String name(CompoundTag components, HolderLookup.Provider registries) {
        if (!components.contains("minecraft:custom_name", Tag.TAG_STRING)) {
            return null;
        }
        String json = components.getString("minecraft:custom_name");
        try {
            Component name = Component.Serializer.fromJson(json, registries);
            return name == null ? json.toLowerCase(Locale.ROOT) : name.getString().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return json.toLowerCase(Locale.ROOT);
        }
    }

    private static void enchantments(CompoundTag tag, List<String> into) {
        // Written with a "levels" map; a bare map is accepted too, as the codec reads both.
        CompoundTag levels = tag.contains("levels", Tag.TAG_COMPOUND) ? tag.getCompound("levels") : tag;
        for (String key : levels.getAllKeys()) {
            if (levels.contains(key, Tag.TAG_ANY_NUMERIC) && !into.contains(key)) {
                into.add(key);
            }
        }
    }
}
