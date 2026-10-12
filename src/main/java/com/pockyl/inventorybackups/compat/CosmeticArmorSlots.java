package com.pockyl.inventorybackups.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Cosmetic Armor Reworked: {@code armor/<index>}, 0 boots to 3 helmet like the armor slots. Its inventory is a NeoForge
 * {@link ItemStackHandler}, so only finding it needs reflection.
 */
final class CosmeticArmorSlots implements SlotProvider {
    private static final String TYPE = "armor";
    @Nullable
    private Method getStacks;

    @Override
    public String id() {
        return "cosmeticarmor";
    }

    @Override
    public boolean available() {
        return ModList.get().isLoaded("cosmeticarmorreworked");
    }

    @Override
    public void read(Player player, BiConsumer<String, ItemStack> slots) throws ReflectiveOperationException {
        ItemStackHandler stacks = stacks(player);
        for (int i = 0; i < stacks.getSlots(); i++) {
            slots.accept(TYPE + "/" + i, stacks.getStackInSlot(i));
        }
    }

    @Override
    public ItemStack get(Player player, String slot) throws ReflectiveOperationException {
        int index = index(player, slot);
        return index < 0 ? ItemStack.EMPTY : stacks(player).getStackInSlot(index);
    }

    @Override
    public boolean set(Player player, String slot, ItemStack stack) throws ReflectiveOperationException {
        int index = index(player, slot);
        if (index < 0) {
            return false;
        }
        // The inventory syncs itself to the clients on every change.
        stacks(player).setStackInSlot(index, stack);
        return true;
    }

    private int index(Player player, String slot) throws ReflectiveOperationException {
        IndexedSlot indexed = SlotProvider.indexed(slot);
        if (indexed == null || !indexed.type().equals(TYPE) || indexed.index() >= stacks(player).getSlots()) {
            return -1;
        }
        return indexed.index();
    }

    private ItemStackHandler stacks(Player player) throws ReflectiveOperationException {
        if (getStacks == null) {
            getStacks = Class.forName("lain.mods.cos.api.CosArmorAPI").getMethod("getCAStacks", UUID.class);
        }
        return (ItemStackHandler) getStacks.invoke(null, player.getUUID());
    }
}
