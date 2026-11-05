package com.pockyl.inventorybackups.fabric.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.inventorybackups.event.CommonEvents;

/**
 * Item pickups: the stack before and after a player touched the item entity, like NeoForge's pickup event.
 */
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @Unique
    private ItemStack inventorybackups$before = ItemStack.EMPTY;

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void inventorybackups$beforeTouch(Player player, CallbackInfo info) {
        if (player instanceof ServerPlayer) {
            inventorybackups$before = ((ItemEntity) (Object) this).getItem().copy();
        }
    }

    @Inject(method = "playerTouch", at = @At("RETURN"))
    private void inventorybackups$afterTouch(Player player, CallbackInfo info) {
        ItemStack before = inventorybackups$before;
        inventorybackups$before = ItemStack.EMPTY;
        ItemEntity self = (ItemEntity) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer) || before.isEmpty()) {
            return;
        }
        // A fully picked up entity is removed (its stack then holds the picked count, not what is left).
        ItemStack left = self.isRemoved() ? ItemStack.EMPTY : self.getItem();
        if (left.getCount() < before.getCount()) {
            CommonEvents.itemPickedUp(serverPlayer, self, before.copyWithCount(before.getCount() - left.getCount()));
        }
    }
}
