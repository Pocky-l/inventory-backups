package com.pockyl.inventorybackups.index;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
 * distinctive stacks (renamed, enchanted or otherwise carrying data), a fingerprint that follows one specific item.
 * Damage and repair cost are left out of the fingerprint, so a sword keeps its fingerprint while it wears down.
 *
 * @param fingerprint 0 for plain stacks
 * @param name        custom name in lower case, null when the stack has none
 */
public record ItemKeys(String itemId, long fingerprint, @Nullable String name, List<String> enchantments) {
    private static final Set<String> IGNORED = Set.of("Damage", "RepairCost");
    /** Capability data Forge saves next to the tag; it belongs to the item as much as the tag does. */
    private static final String CAPABILITIES = "ForgeCaps";
    public static final String ITEM_PREFIX = "i:";
    public static final String FINGERPRINT_PREFIX = "f:";

    public static ItemKeys of(CompoundTag tag, HolderLookup.Provider registries) {
        String id = tag.getString("id");
        CompoundTag data = tag.getCompound("tag");
        CompoundTag distinctive = new CompoundTag();
        for (String key : data.getAllKeys()) {
            if (!IGNORED.contains(key)) {
                distinctive.put(key, data.get(key));
            }
        }
        long fingerprint = 0;
        if (!distinctive.isEmpty() || tag.contains(CAPABILITIES, Tag.TAG_COMPOUND)) {
            CompoundTag identity = new CompoundTag();
            identity.putString("id", id);
            identity.put("tag", distinctive);
            if (tag.contains(CAPABILITIES, Tag.TAG_COMPOUND)) {
                identity.put(CAPABILITIES, tag.getCompound(CAPABILITIES));
            }
            fingerprint = NbtHash.hash(identity);
            // 0 means "plain stack"; a real hash of 0 is astronomically unlikely but must not collide with it.
            if (fingerprint == 0) {
                fingerprint = 1;
            }
        }
        List<String> enchantments = new ArrayList<>();
        enchantments(data.getList("Enchantments", Tag.TAG_COMPOUND), enchantments);
        enchantments(data.getList("StoredEnchantments", Tag.TAG_COMPOUND), enchantments);
        return new ItemKeys(id, fingerprint, name(data), List.copyOf(enchantments));
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
    private static String name(CompoundTag data) {
        CompoundTag display = data.getCompound("display");
        if (!display.contains("Name", Tag.TAG_STRING)) {
            return null;
        }
        String json = display.getString("Name");
        try {
            Component name = Component.Serializer.fromJson(json);
            return name == null ? json.toLowerCase(Locale.ROOT) : name.getString().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return json.toLowerCase(Locale.ROOT);
        }
    }

    /** Ids of a list of {@code {id, lvl}} enchantments. */
    private static void enchantments(ListTag list, List<String> into) {
        for (Tag tag : list) {
            String id = ((CompoundTag) tag).getString("id");
            if (!id.isEmpty() && !into.contains(id)) {
                into.add(id);
            }
        }
    }
}
