package com.pockyl.inventorybackups.backup;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import com.pockyl.inventorybackups.InventoryBackups;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Contents of one backup file: the non-empty slots of an inventory or ender chest. Serialized with Gson, so the
 * {@code data} field name is part of the file format.
 */
public final class BackupData {
    private List<ItemData> data = new ArrayList<>();

    public static BackupData encode(HolderLookup.Provider registries, Map<Integer, ItemStack> slots) {
        BackupData backup = new BackupData();
        new TreeMap<>(slots).forEach((index, stack) -> {
            if (!stack.isEmpty()) {
                backup.data.add(new ItemData(index, stack.save(registries).toString()));
            }
        });
        return backup;
    }

    /** Returns the stored items by slot index; items that cannot be read any more are skipped. */
    public Map<Integer, ItemStack> decode(HolderLookup.Provider registries) {
        Map<Integer, ItemStack> slots = new TreeMap<>();
        for (ItemData item : data == null ? List.<ItemData>of() : data) {
            if (item == null || item.nbt() == null) {
                continue;
            }
            try {
                ItemStack stack = ItemStack.parseOptional(registries, TagParser.parseTag(item.nbt()));
                if (!stack.isEmpty()) {
                    slots.put(item.index(), stack);
                }
            } catch (CommandSyntaxException e) {
                InventoryBackups.LOGGER.warn("Skipping unreadable item in slot {}: {}", item.index(), e.getMessage());
            }
        }
        return slots;
    }

    public int size() {
        return data == null ? 0 : data.size();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BackupData other && Objects.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(data);
    }
}
