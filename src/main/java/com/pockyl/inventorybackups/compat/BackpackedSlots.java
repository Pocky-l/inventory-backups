package com.pockyl.inventorybackups.compat;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The backpack slots of Backpacked: {@code back/<index>}, one per equippable backpack. The backpacks keep their
 * contents in the item.
 * <p>
 * Backpacked 3 for 1.20.1 has the same helper as on newer versions. Backpacked 2 has a single backpack slot, kept in an
 * extended player inventory; with Curios installed it uses a Curios slot instead, which is recorded there.
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
        if (index < 0 || !stack.isEmpty() && !api.backpackItem.isInstance(stack.getItem())) {
            return false;
        }
        return api.helper != null ? setModern(api, player, stack, index) : setLegacy(api, player, stack, index);
    }

    private static boolean setModern(Api api, Player player, ItemStack stack, int index) throws ReflectiveOperationException {
        // Refuses slots that are not unlocked and items that are not backpacks.
        if (!(boolean) api.setBackpackStack.invoke(null, player, stack, index)) {
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

    @SuppressWarnings("unchecked")
    private static boolean setLegacy(Api api, Player player, ItemStack stack, int index) throws ReflectiveOperationException {
        List<?> backpacks = api.backpacks(player);
        if (index >= backpacks.size()) {
            return false;
        }
        // Part of the player's inventory: the inventory screen syncs it to the player, Backpacked's tick to the others.
        ((List<ItemStack>) backpacks).set(index, stack);
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
        final Class<?> backpackItem;
        /** Backpacked 3. */
        @Nullable
        final Class<?> helper;
        @Nullable
        final Method getBackpacks;
        @Nullable
        final Method setBackpackStack;
        @Nullable
        final Object key;
        @Nullable
        final Method setValue;
        /** Backpacked 2: whether the backpack is worn in a Curios slot. */
        @Nullable
        final Method usesCurios;

        Api() throws ReflectiveOperationException {
            backpackItem = Class.forName("com.mrcrayfish.backpacked.item.BackpackItem");
            helper = find("com.mrcrayfish.backpacked.BackpackHelper");
            if (helper != null) {
                getBackpacks = helper.getMethod("getBackpacks", Player.class);
                setBackpackStack = helper.getMethod("setBackpackStack", Player.class, ItemStack.class, int.class);
                key = Class.forName("com.mrcrayfish.backpacked.core.ModSyncedDataKeys").getField("BACKPACKS").get(null);
                setValue = Class.forName("com.mrcrayfish.framework.api.sync.SyncedDataKey").getMethod("setValue", Entity.class,
                        Object.class);
                usesCurios = null;
            } else {
                getBackpacks = null;
                setBackpackStack = null;
                key = null;
                setValue = null;
                usesCurios = Class.forName("com.mrcrayfish.backpacked.Backpacked").getMethod("isCuriosLoaded");
            }
        }

        @Nullable
        private static Class<?> find(String name) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        /** The equipped backpacks, the live list; empty when Backpacked 2 keeps its backpack in a Curios slot. */
        List<?> backpacks(Player player) throws ReflectiveOperationException {
            if (helper != null) {
                return (List<?>) getBackpacks.invoke(null, player);
            }
            if ((boolean) usesCurios.invoke(null)) {
                return List.of();
            }
            Method getBackpackItems = player.getInventory().getClass().getMethod("getBackpackItems");
            return (List<?>) getBackpackItems.invoke(player.getInventory());
        }
    }
}
