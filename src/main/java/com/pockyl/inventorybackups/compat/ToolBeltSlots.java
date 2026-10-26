package com.pockyl.inventorybackups.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

/**
 * The belt slot of Tool Belt: {@code belt}. The belt keeps its tools in the item. With Curios installed the belt can be
 * worn in a Curios slot instead (the mod's own slot is then off) and is recorded there.
 */
final class ToolBeltSlots implements SlotProvider {
    private static final String SLOT = "belt";
    @Nullable
    private Api api;

    @Override
    public String id() {
        return "toolbelt";
    }

    @Override
    public boolean available() {
        return ModList.get().isLoaded("toolbelt");
    }

    @Override
    public void read(Player player, BiConsumer<String, ItemStack> slots) throws ReflectiveOperationException {
        Api api = api();
        Object belt = api.belt(player);
        if (belt != null) {
            slots.accept(SLOT, (ItemStack) api.getContents.invoke(belt));
        }
    }

    @Override
    public ItemStack get(Player player, String slot) throws ReflectiveOperationException {
        Api api = api();
        Object belt = SLOT.equals(slot) ? api.belt(player) : null;
        return belt == null ? ItemStack.EMPTY : (ItemStack) api.getContents.invoke(belt);
    }

    @Override
    public boolean set(Player player, String slot, ItemStack stack) throws ReflectiveOperationException {
        Api api = api();
        Object belt = SLOT.equals(slot) ? api.belt(player) : null;
        if (belt == null || !stack.isEmpty() && !(boolean) api.canEquip.invoke(belt, stack)) {
            return false;
        }
        // Syncs to the player and everyone tracking them.
        api.setContents.invoke(belt, stack);
        return true;
    }

    @Override
    public boolean accepts(Player player, String slot, ItemStack stack) {
        try {
            Api api = api();
            Object belt = api.belt(player);
            return belt != null && (boolean) api.canEquip.invoke(belt, stack);
        } catch (ReflectiveOperationException | RuntimeException e) {
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
        final Method get;
        final Method getBelt;
        final Method getContents;
        final Method setContents;
        final Method canEquip;
        final Field slotEnabled;

        Api() throws ReflectiveOperationException {
            Class<?> container = Class.forName("dev.gigaherz.toolbelt.slot.BeltExtensionSlot");
            Class<?> slot = Class.forName("dev.gigaherz.toolbelt.customslots.IExtensionSlot");
            get = container.getMethod("get", LivingEntity.class);
            getBelt = container.getMethod("getBelt");
            getContents = slot.getMethod("getContents");
            setContents = slot.getMethod("setContents", ItemStack.class);
            canEquip = slot.getMethod("canEquip", ItemStack.class);
            slotEnabled = Class.forName("dev.gigaherz.toolbelt.ConfigData").getField("customBeltSlotEnabled");
        }

        /** The belt slot of the player; null when there is none or the mod's own slot is off. */
        @Nullable
        Object belt(Player player) throws ReflectiveOperationException {
            if (!slotEnabled.getBoolean(null)) {
                return null;
            }
            Object container = ((LazyOptional<?>) get.invoke(null, player)).resolve().orElse(null);
            return container == null ? null : getBelt.invoke(container);
        }
    }
}
