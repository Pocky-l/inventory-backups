package com.pockyl.inventorybackups.compat;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The backpack slots of Backpacked: {@code back/<index>}, one per equippable backpack. The backpacks keep their
 * contents in the item.
 */
final class BackpackedSlots implements SlotProvider {
    private static final String TYPE = "back";
    @Nullable
    private Api api;

    @Override
    public String id() {
        return "backpacked";
    }

    @Override
    public boolean available() {
        return ModList.get().isLoaded("backpacked");
    }

    @Override
    public void read(Player player, BiConsumer<String, ItemStack> slots) throws ReflectiveOperationException {
        List<?> backpacks = api().backpacks(player);
        for (int i = 0; i < backpacks.size(); i++) {
            slots.accept(TYPE + "/" + i, (ItemStack) backpacks.get(i));
        }
    }

    @Override
    public ItemStack get(Player player, String slot) throws ReflectiveOperationException {
        int index = index(slot);
        List<?> backpacks = api().backpacks(player);
        return index < 0 || index >= backpacks.size() ? ItemStack.EMPTY : (ItemStack) backpacks.get(index);
    }

    @Override
    public boolean set(Player player, String slot, ItemStack stack) throws ReflectiveOperationException {
        int index = index(slot);
        Api api = api();
        // Refuses slots that are not unlocked and items that are not backpacks.
        if (index < 0 || !(boolean) api.setBackpackStack.invoke(null, player, stack, index)) {
            return false;
        }
        // A list of copies counts as a changed value, so the change reaches the clients and is saved.
        List<?> backpacks = api.backpacks(player);
        NonNullList<ItemStack> copy = NonNullList.withSize(backpacks.size(), ItemStack.EMPTY);
        for (int i = 0; i < backpacks.size(); i++) {
            copy.set(i, ((ItemStack) backpacks.get(i)).copy());
        }
        api.setValue.invoke(api.key, player, copy);
        return true;
    }

    @Override
    public boolean accepts(Player player, String slot, ItemStack stack) {
        try {
            return api().backpackItem.isInstance(stack.getItem());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static int index(String slot) {
        IndexedSlot indexed = SlotProvider.indexed(slot);
        return indexed != null && indexed.type().equals(TYPE) ? indexed.index() : -1;
    }

    private Api api() throws ReflectiveOperationException {
        if (api == null) {
            api = new Api();
        }
        return api;
    }

    private static final class Api {
        final Method getBackpacks;
        final Method setBackpackStack;
        final Object key;
        final Method setValue;
        final Class<?> backpackItem;

        Api() throws ReflectiveOperationException {
            Class<?> helper = Class.forName("com.mrcrayfish.backpacked.BackpackHelper");
            getBackpacks = helper.getMethod("getBackpacks", Player.class);
            setBackpackStack = helper.getMethod("setBackpackStack", Player.class, ItemStack.class, int.class);
            key = Class.forName("com.mrcrayfish.backpacked.core.ModSyncedDataKeys").getField("BACKPACKS").get(null);
            setValue = Class.forName("com.mrcrayfish.framework.api.sync.SyncedDataKey").getMethod("setValue", Entity.class,
                    Object.class);
            backpackItem = Class.forName("com.mrcrayfish.backpacked.item.BackpackItem");
        }

        List<?> backpacks(Player player) throws ReflectiveOperationException {
            return (List<?>) getBackpacks.invoke(null, player);
        }
    }
}
