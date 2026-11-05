package com.pockyl.inventorybackups.backup;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Item counts regardless of slots: identical items (same item and tag) are added up.
 */
final class ItemTally {
    private final List<ItemStack> prototypes = new ArrayList<>();
    private final List<Integer> counts = new ArrayList<>();

    void add(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (int i = 0; i < prototypes.size(); i++) {
            if (ItemStack.isSameItemSameTags(prototypes.get(i), stack)) {
                counts.set(i, counts.get(i) + stack.getCount());
                return;
            }
        }
        prototypes.add(stack.copyWithCount(1));
        counts.add(stack.getCount());
    }

    int count(ItemStack prototype) {
        for (int i = 0; i < prototypes.size(); i++) {
            if (ItemStack.isSameItemSameTags(prototypes.get(i), prototype)) {
                return counts.get(i);
            }
        }
        return 0;
    }

    /** Prototypes (count one) of every item in this tally or the other one. */
    List<ItemStack> union(ItemTally other) {
        ItemTally all = new ItemTally();
        prototypes.forEach(all::add);
        other.prototypes.forEach(all::add);
        return all.prototypes;
    }

    List<ItemStack> prototypes() {
        return prototypes;
    }
}
