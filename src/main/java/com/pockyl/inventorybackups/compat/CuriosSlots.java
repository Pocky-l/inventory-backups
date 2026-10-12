package com.pockyl.inventorybackups.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Curios API slots: {@code <type>/<index>} and, for slot types with cosmetic slots, {@code cosmetic/<type>/<index>}.
 * With the Accessories compatibility layer installed, Curios slots are Accessories slots and come from
 * {@link AccessoriesSlots} instead.
 */
final class CuriosSlots implements SlotProvider {
    private static final String COSMETIC = "cosmetic/";
    @Nullable
    private Api api;

    @Override
    public String id() {
        return "curios";
    }

    @Override
    public boolean available() {
        return ModList.get().isLoaded("curios") && !ModList.get().isLoaded("cclayer");
    }

    @Override
    public void read(Player player, BiConsumer<String, ItemStack> slots) throws ReflectiveOperationException {
        Api api = api();
        for (Map.Entry<?, ?> type : api.curios(player).entrySet()) {
            Object handler = type.getValue();
            String id = (String) type.getKey();
            readStacks(api, api.getStacks.invoke(handler), id, slots);
            if ((boolean) api.hasCosmetic.invoke(handler)) {
                readStacks(api, api.getCosmeticStacks.invoke(handler), COSMETIC + id, slots);
            }
        }
    }

    private static void readStacks(Api api, Object stacks, String type, BiConsumer<String, ItemStack> slots)
            throws ReflectiveOperationException {
        int size = (int) api.getSlots.invoke(stacks);
        for (int i = 0; i < size; i++) {
            slots.accept(type + "/" + i, (ItemStack) api.getStackInSlot.invoke(stacks, i));
        }
    }

    @Override
    public ItemStack get(Player player, String slot) throws ReflectiveOperationException {
        Api api = api();
        Object stacks = stacks(api, player, slot);
        IndexedSlot indexed = SlotProvider.indexed(slot);
        if (stacks == null || indexed == null || indexed.index() >= (int) api.getSlots.invoke(stacks)) {
            return ItemStack.EMPTY;
        }
        return (ItemStack) api.getStackInSlot.invoke(stacks, indexed.index());
    }

    @Override
    public boolean set(Player player, String slot, ItemStack stack) throws ReflectiveOperationException {
        Api api = api();
        Object stacks = stacks(api, player, slot);
        IndexedSlot indexed = SlotProvider.indexed(slot);
        if (stacks == null || indexed == null || indexed.index() >= (int) api.getSlots.invoke(stacks)) {
            return false;
        }
        // Curios compares the slots with their previous stacks every tick, which equips, unequips and syncs the change.
        api.setStackInSlot.invoke(stacks, indexed.index(), stack);
        return true;
    }

    /** The plain (not cosmetic) slots in the order Curios lists them: what the curio numbers of 2.x backups count. */
    List<String> legacyOrder(Player player) throws ReflectiveOperationException {
        Api api = api();
        List<String> order = new ArrayList<>();
        for (Map.Entry<?, ?> type : api.curios(player).entrySet()) {
            int size = (int) api.getSlots.invoke(api.getStacks.invoke(type.getValue()));
            for (int i = 0; i < size; i++) {
                order.add(type.getKey() + "/" + i);
            }
        }
        return order;
    }

    @Nullable
    private static Object stacks(Api api, Player player, String slot) throws ReflectiveOperationException {
        boolean cosmetic = slot.startsWith(COSMETIC);
        IndexedSlot indexed = SlotProvider.indexed(cosmetic ? slot.substring(COSMETIC.length()) : slot);
        if (indexed == null) {
            return null;
        }
        Object handler = api.curios(player).get(indexed.type());
        if (handler == null) {
            return null;
        }
        if (cosmetic && !(boolean) api.hasCosmetic.invoke(handler)) {
            return null;
        }
        return cosmetic ? api.getCosmeticStacks.invoke(handler) : api.getStacks.invoke(handler);
    }

    private Api api() throws ReflectiveOperationException {
        if (api == null) {
            api = new Api();
        }
        return api;
    }

    /** The Curios methods used here, looked up once on the public API interfaces. */
    private static final class Api {
        final Method getCuriosInventory;
        final Method getCurios;
        final Method getStacks;
        final Method getCosmeticStacks;
        final Method hasCosmetic;
        final Method getSlots;
        final Method getStackInSlot;
        final Method setStackInSlot;

        Api() throws ReflectiveOperationException {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> inventory = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Class<?> stacksHandler = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            Class<?> dynamicStacks = Class.forName("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler");
            getCuriosInventory = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
            getCurios = inventory.getMethod("getCurios");
            getStacks = stacksHandler.getMethod("getStacks");
            getCosmeticStacks = stacksHandler.getMethod("getCosmeticStacks");
            hasCosmetic = stacksHandler.getMethod("hasCosmetic");
            getSlots = dynamicStacks.getMethod("getSlots");
            getStackInSlot = dynamicStacks.getMethod("getStackInSlot", int.class);
            setStackInSlot = dynamicStacks.getMethod("setStackInSlot", int.class, ItemStack.class);
        }

        /** Slot type id -> stacks handler; empty when the player has no Curios inventory. */
        Map<?, ?> curios(Player player) throws ReflectiveOperationException {
            Optional<?> inventory = (Optional<?>) getCuriosInventory.invoke(null, player);
            return inventory.isEmpty() ? Map.of() : (Map<?, ?>) getCurios.invoke(inventory.get());
        }
    }
}
