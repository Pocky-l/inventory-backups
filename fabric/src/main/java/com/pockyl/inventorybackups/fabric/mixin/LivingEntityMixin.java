package com.pockyl.inventorybackups.fabric.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.inventorybackups.event.CommonEvents;
import com.pockyl.inventorybackups.fabric.DeathDrops;

/**
 * Items players drop: collected on death and reported together like NeoForge's drops event, otherwise tosses.
 * Every player drop is made by {@code createItemStackToDrop} on this Minecraft version.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void inventorybackups$startDrops(ServerLevel level, DamageSource source, CallbackInfo info) {
        if ((Object) this instanceof ServerPlayer player) {
            DeathDrops.start(player);
        }
    }

    @Inject(method = "createItemStackToDrop", at = @At("RETURN"))
    private void inventorybackups$dropped(ItemStack stack, boolean randomly, boolean thrownFromHand,
                                          CallbackInfoReturnable<ItemEntity> info) {
        ItemEntity entity = info.getReturnValue();
        if ((Object) this instanceof ServerPlayer player && entity != null && !DeathDrops.collect(player, entity)) {
            CommonEvents.itemTossed(player, entity);
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
    private void inventorybackups$finishDrops(ServerLevel level, DamageSource source, CallbackInfo info) {
        if ((Object) this instanceof ServerPlayer player) {
            DeathDrops.finish(player);
        }
    }
}
