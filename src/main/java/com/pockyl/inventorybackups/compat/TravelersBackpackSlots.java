package com.pockyl.inventorybackups.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The worn backpack of Traveler's Backpack: {@code back}. The backpack keeps its contents in the item, so backing up
 * the item backs up the contents. With its Curios or Accessories integration on, the backpack is worn in their slots
 * and recorded there instead.
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
        Object attachment = api().attachment(player);
        if (attachment != null) {
            slots.accept(SLOT, (ItemStack) api.getBackpack.invoke(attachment));
        }
    }

    @Override
    public ItemStack get(Player player, String slot) throws ReflectiveOperationException {
        Object attachment = SLOT.equals(slot) ? api().attachment(player) : null;
        return attachment == null ? ItemStack.EMPTY : (ItemStack) api.getBackpack.invoke(attachment);
    }

    @Override
    public boolean set(Player player, String slot, ItemStack stack) throws ReflectiveOperationException {
        Object attachment = SLOT.equals(slot) ? api().attachment(player) : null;
        if (attachment == null || !stack.isEmpty() && !api.isBackpack(stack)) {
            return false;
        }
        // Both remove the old backpack and sync the change to the player and everyone tracking them.
        if (stack.isEmpty()) {
            api.remove.invoke(attachment);
        } else {
            api.equipBackpack.invoke(attachment, stack);
        }
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
        final Method getAttachment;
        final Method getBackpack;
        final Method equipBackpack;
        final Method remove;
        final Class<?> backpackItem;

        Api() throws ReflectiveOperationException {
            Class<?> utils = Class.forName("com.tiviacz.travelersbackpack.capability.AttachmentUtils");
            Class<?> attachment = Class.forName("com.tiviacz.travelersbackpack.capability.ITravelersBackpack");
            getAttachment = utils.getMethod("getAttachment", Player.class);
            getBackpack = attachment.getMethod("getBackpack");
            equipBackpack = attachment.getMethod("equipBackpack", ItemStack.class);
            remove = attachment.getMethod("remove");
            backpackItem = Class.forName("com.tiviacz.travelersbackpack.items.TravelersBackpackItem");
        }

        @Nullable
        Object attachment(Player player) throws ReflectiveOperationException {
            return ((Optional<?>) getAttachment.invoke(null, player)).orElse(null);
        }

        boolean isBackpack(ItemStack stack) {
            return backpackItem.isInstance(stack.getItem());
        }
    }
}
