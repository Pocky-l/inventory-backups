package com.pockyl.inventorybackups.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.function.BiConsumer;

/**
 * The belt slot of Tool Belt: {@code belt}. The belt keeps its tools in the item. With Curios installed the belt can be
 * worn in a Curios slot instead and is recorded there.
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
        Object attachment = api.attachment(player);
        if (attachment != null) {
            slots.accept(SLOT, (ItemStack) api.getContents.invoke(attachment));
        }
    }

    @Override
    public ItemStack get(Player player, String slot) throws ReflectiveOperationException {
        Api api = api();
        Object attachment = SLOT.equals(slot) ? api.attachment(player) : null;
        return attachment == null ? ItemStack.EMPTY : (ItemStack) api.getContents.invoke(attachment);
    }

    @Override
    public boolean set(Player player, String slot, ItemStack stack) throws ReflectiveOperationException {
        Api api = api();
        Object attachment = SLOT.equals(slot) ? api.attachment(player) : null;
        if (attachment == null || !stack.isEmpty() && !(boolean) api.canEquip.invoke(attachment, stack)) {
            return false;
        }
        // Syncs to the player and everyone tracking them.
        api.setContents.invoke(attachment, stack);
        return true;
    }

    @Override
    public boolean accepts(Player player, String slot, ItemStack stack) {
        try {
            Api api = api();
            Object attachment = api.attachment(player);
            return attachment != null && (boolean) api.canEquip.invoke(attachment, stack);
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
        final Method getContents;
        final Method setContents;
        final Method canEquip;

        Api() throws ReflectiveOperationException {
            Class<?> attachment = Class.forName("dev.gigaherz.toolbelt.slot.BeltAttachment");
            get = attachment.getMethod("get", LivingEntity.class);
            getContents = attachment.getMethod("getContents");
            setContents = attachment.getMethod("setContents", ItemStack.class);
            canEquip = attachment.getMethod("canEquip", ItemStack.class);
        }

        @Nullable
        Object attachment(Player player) throws ReflectiveOperationException {
            return get.invoke(null, player);
        }
    }
}
