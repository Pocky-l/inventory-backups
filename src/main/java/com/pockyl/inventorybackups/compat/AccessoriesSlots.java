package com.pockyl.inventorybackups.compat;

import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Accessories slots: {@code <slot name>/<index>} and {@code cosmetic/<slot name>/<index>}. Its containers are vanilla
 * {@link Container}s, so only finding them needs reflection.
 */
final class AccessoriesSlots implements SlotProvider {
    private static final String COSMETIC = "cosmetic/";
    @Nullable
    private Api api;

    @Override
    public String id() {
        return "accessories";
    }

    @Override
    public boolean available() {
        return ModList.get().isLoaded("accessories");
    }

    @Override
    public void read(Player player, BiConsumer<String, ItemStack> slots) throws ReflectiveOperationException {
        Api api = api();
        for (Map.Entry<?, ?> entry : api.containers(player).entrySet()) {
            String name = (String) entry.getKey();
            readContainer((Container) api.getAccessories.invoke(entry.getValue()), name, slots);
            readContainer((Container) api.getCosmeticAccessories.invoke(entry.getValue()), COSMETIC + name, slots);
        }
    }

    private static void readContainer(@Nullable Container container, String type, BiConsumer<String, ItemStack> slots) {
        if (container == null) {
            return;
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            slots.accept(type + "/" + i, container.getItem(i));
        }
    }

    @Override
    public ItemStack get(Player player, String slot) throws ReflectiveOperationException {
        Target target = target(player, slot);
        return target == null ? ItemStack.EMPTY : target.container().getItem(target.index());
    }

    @Override
    public boolean set(Player player, String slot, ItemStack stack) throws ReflectiveOperationException {
        Target target = target(player, slot);
        if (target == null) {
            return false;
        }
        // Accessories compares the slots with their previous stacks every tick, which equips, unequips and syncs.
        target.container().setItem(target.index(), stack);
        api().markChanged.invoke(target.owner());
        return true;
    }

    private record Target(Object owner, Container container, int index) {
    }

    @Nullable
    private Target target(Player player, String slot) throws ReflectiveOperationException {
        Api api = api();
        boolean cosmetic = slot.startsWith(COSMETIC);
        IndexedSlot indexed = SlotProvider.indexed(cosmetic ? slot.substring(COSMETIC.length()) : slot);
        if (indexed == null) {
            return null;
        }
        Object owner = api.containers(player).get(indexed.type());
        if (owner == null) {
            return null;
        }
        Container container = (Container) (cosmetic ? api.getCosmeticAccessories : api.getAccessories).invoke(owner);
        if (container == null || indexed.index() >= container.getContainerSize()) {
            return null;
        }
        return new Target(owner, container, indexed.index());
    }

    private Api api() throws ReflectiveOperationException {
        if (api == null) {
            api = new Api();
        }
        return api;
    }

    private static final class Api {
        final Method getOptionally;
        final Method getContainers;
        final Method getAccessories;
        final Method getCosmeticAccessories;
        final Method markChanged;

        Api() throws ReflectiveOperationException {
            Class<?> capability = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
            Class<?> container = Class.forName("io.wispforest.accessories.api.AccessoriesContainer");
            getOptionally = capability.getMethod("getOptionally", LivingEntity.class);
            getContainers = capability.getMethod("getContainers");
            getAccessories = container.getMethod("getAccessories");
            getCosmeticAccessories = container.getMethod("getCosmeticAccessories");
            markChanged = container.getMethod("markChanged");
        }

        /** Slot name -> container; empty when the player has no accessories. */
        Map<?, ?> containers(Player player) throws ReflectiveOperationException {
            Optional<?> capability = (Optional<?>) getOptionally.invoke(null, player);
            return capability.isEmpty() ? Map.of() : (Map<?, ?>) getContainers.invoke(capability.get());
        }
    }
}
