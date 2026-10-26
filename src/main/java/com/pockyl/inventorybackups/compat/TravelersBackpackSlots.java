package com.pockyl.inventorybackups.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.function.BiConsumer;

/**
 * The worn backpack of Traveler's Backpack: {@code back}. The backpack keeps its contents in the item, so backing up
 * the item backs up the contents. With its Curios integration on, the backpack is worn in a Curios slot and recorded
 * there instead.
 * <p>
 * The capability changed within the 1.20.1 versions: up to 9.1.16 it has {@code getWearable}, {@code setWearable} and
 * separate sync calls, from 9.1.18 on {@code getBackpack}, {@code equipBackpack} and {@code remove}, which sync
 * themselves. Both are supported.
 */
final class TravelersBackpackSlots implements SlotProvider {
    private static final String SLOT = "back";
    @Nullable
    private Api api;

    @Override
    public String id() {
        return "travelersbackpack";
    }

    @Override
    public boolean available() {
        return ModList.get().isLoaded("travelersbackpack");
    }

    @Override
    public void read(Player player, BiConsumer<String, ItemStack> slots) throws ReflectiveOperationException {
        Object capability = api().capability(player);
        if (capability != null) {
            slots.accept(SLOT, api.backpack(capability));
        }
    }

    @Override
    public ItemStack get(Player player, String slot) throws ReflectiveOperationException {
        Object capability = SLOT.equals(slot) ? api().capability(player) : null;
        return capability == null ? ItemStack.EMPTY : api.backpack(capability);
    }

    @Override
    public boolean set(Player player, String slot, ItemStack stack) throws ReflectiveOperationException {
        Object capability = SLOT.equals(slot) ? api().capability(player) : null;
        if (capability == null || !stack.isEmpty() && !api.isBackpack(stack)) {
            return false;
        }
        api.put(capability, player, stack);
        return true;
    }

    @Override
    public boolean accepts(Player player, String slot, ItemStack stack) {
        try {
            return api().isBackpack(stack);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private Api api() throws ReflectiveOperationException {
        if (api == null) {
            api = new Api();
        }
        return api;
    }

    private static final class Api {
        final Method getCapability;
        final Method enableCurios;
        final Class<?> backpackItem;
        /** From 9.1.18 on. */
        @Nullable
        final Method getBackpack;
        @Nullable
        final Method equipBackpack;
        @Nullable
        final Method remove;
        /** Up to 9.1.16. */
        @Nullable
        final Method getWearable;
        @Nullable
        final Method setWearable;
        @Nullable
        final Method setContents;
        @Nullable
        final Method removeWearable;
        @Nullable
        final Method synchronise;
        @Nullable
        final Method synchroniseToOthers;

        Api() throws ReflectiveOperationException {
            Class<?> utils = Class.forName("com.tiviacz.travelersbackpack.capability.CapabilityUtils");
            Class<?> capability = Class.forName("com.tiviacz.travelersbackpack.capability.ITravelersBackpack");
            getCapability = utils.getMethod("getCapability", Player.class);
            enableCurios = Class.forName("com.tiviacz.travelersbackpack.TravelersBackpack").getMethod("enableCurios");
            backpackItem = Class.forName("com.tiviacz.travelersbackpack.items.TravelersBackpackItem");
            getBackpack = find(capability, "getBackpack");
            if (getBackpack != null) {
                equipBackpack = capability.getMethod("equipBackpack", ItemStack.class);
                remove = capability.getMethod("remove");
                getWearable = null;
                setWearable = null;
                setContents = null;
                removeWearable = null;
                synchronise = null;
                synchroniseToOthers = null;
            } else {
                equipBackpack = null;
                remove = null;
                getWearable = capability.getMethod("getWearable");
                setWearable = capability.getMethod("setWearable", ItemStack.class);
                setContents = capability.getMethod("setContents", ItemStack.class);
                removeWearable = capability.getMethod("removeWearable");
                synchronise = capability.getMethod("synchronise");
                synchroniseToOthers = capability.getMethod("synchroniseToOthers", Player.class);
            }
        }

        @Nullable
        private static Method find(Class<?> type, String name) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        /** The player's backpack capability; null when there is none or the backpack is worn in a Curios slot. */
        @Nullable
        Object capability(Player player) throws ReflectiveOperationException {
            if ((boolean) enableCurios.invoke(null)) {
                return null;
            }
            return ((LazyOptional<?>) getCapability.invoke(null, player)).resolve().orElse(null);
        }

        ItemStack backpack(Object capability) throws ReflectiveOperationException {
            return (ItemStack) (getBackpack != null ? getBackpack : getWearable).invoke(capability);
        }

        /** Replaces the worn backpack and syncs the change to the player and everyone tracking them. */
        void put(Object capability, Player player, ItemStack stack) throws ReflectiveOperationException {
            if (getBackpack != null) {
                if (stack.isEmpty()) {
                    remove.invoke(capability);
                } else {
                    equipBackpack.invoke(capability, stack);
                }
                return;
            }
            if (stack.isEmpty()) {
                removeWearable.invoke(capability);
            } else {
                setWearable.invoke(capability, stack);
                setContents.invoke(capability, stack);
            }
            synchronise.invoke(capability);
            synchroniseToOthers.invoke(capability, player);
        }

        boolean isBackpack(ItemStack stack) {
            return backpackItem.isInstance(stack.getItem());
        }
    }
}
