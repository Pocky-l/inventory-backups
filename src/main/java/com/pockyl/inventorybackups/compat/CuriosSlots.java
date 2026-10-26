package com.pockyl.inventorybackups.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;
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
            readStacks(api.stacks(handler), id, slots);
            if ((boolean) api.hasCosmetic.invoke(handler)) {
                readStacks(api.cosmeticStacks(handler), COSMETIC + id, slots);
            }
        }
    }

    private static void readStacks(IItemHandlerModifiable stacks, String type, BiConsumer<String, ItemStack> slots) {
        for (int i = 0; i < stacks.getSlots(); i++) {
            slots.accept(type + "/" + i, stacks.getStackInSlot(i));
        }
    }

    @Override
    public ItemStack get(Player player, String slot) throws ReflectiveOperationException {
        IItemHandlerModifiable stacks = stacks(api(), player, slot);
        IndexedSlot indexed = SlotProvider.indexed(slot);
        if (stacks == null || indexed == null || indexed.index() >= stacks.getSlots()) {
            return ItemStack.EMPTY;
        }
        return stacks.getStackInSlot(indexed.index());
    }

    @Override
    public boolean set(Player player, String slot, ItemStack stack) throws ReflectiveOperationException {
        IItemHandlerModifiable stacks = stacks(api(), player, slot);
        IndexedSlot indexed = SlotProvider.indexed(slot);
        if (stacks == null || indexed == null || indexed.index() >= stacks.getSlots()) {
            return false;
        }
        // Curios compares the slots with their previous stacks every tick, which equips, unequips and syncs the change.
        stacks.setStackInSlot(indexed.index(), stack);
        return true;
    }

    /** The plain (not cosmetic) slots in the order Curios lists them: what the curio numbers of 2.x backups count. */
    List<String> legacyOrder(Player player) throws ReflectiveOperationException {
        Api api = api();
        List<String> order = new ArrayList<>();
        for (Map.Entry<?, ?> type : api.curios(player).entrySet()) {
            int size = api.stacks(type.getValue()).getSlots();
            for (int i = 0; i < size; i++) {
                order.add(type.getKey() + "/" + i);
            }
        }
        return order;
    }

    @Nullable
    private static IItemHandlerModifiable stacks(Api api, Player player, String slot) throws ReflectiveOperationException {
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
        return cosmetic ? api.cosmeticStacks(handler) : api.stacks(handler);
    }

    private Api api() throws ReflectiveOperationException {
        if (api == null) {
            api = new Api();
        }
        return api;
    }

    /**
     * The Curios methods used here, looked up once on the public API interfaces. The inventory is found through its
     * capability, which every Curios 5 version for 1.20.1 has; the stack handlers are Forge item handlers.
     */
    private static final class Api {
        final Capability<?> inventory;
        final Method getCurios;
        final Method getStacks;
        final Method getCosmeticStacks;
        final Method hasCosmetic;

        Api() throws ReflectiveOperationException {
            Class<?> capability = Class.forName("top.theillusivec4.curios.api.CuriosCapability");
            Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Class<?> stacksHandler = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            inventory = (Capability<?>) capability.getField("INVENTORY").get(null);
            getCurios = handler.getMethod("getCurios");
            getStacks = stacksHandler.getMethod("getStacks");
            getCosmeticStacks = stacksHandler.getMethod("getCosmeticStacks");
            hasCosmetic = stacksHandler.getMethod("hasCosmetic");
        }

        /** Slot type id -> stacks handler; empty when the player has no Curios inventory. */
        Map<?, ?> curios(Player player) throws ReflectiveOperationException {
            Optional<?> handler = player.getCapability(inventory).resolve();
            return handler.isEmpty() ? Map.of() : (Map<?, ?>) getCurios.invoke(handler.get());
        }

        IItemHandlerModifiable stacks(Object handler) throws ReflectiveOperationException {
            return (IItemHandlerModifiable) getStacks.invoke(handler);
        }

        IItemHandlerModifiable cosmeticStacks(Object handler) throws ReflectiveOperationException {
            return (IItemHandlerModifiable) getCosmeticStacks.invoke(handler);
        }
    }
}
