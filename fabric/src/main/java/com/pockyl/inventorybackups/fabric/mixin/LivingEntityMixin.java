package com.pockyl.inventorybackups.fabric.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.inventorybackups.fabric.DeathDrops;

/**
 * Collects the items a player drops on death, reported together like NeoForge's drops event.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void inventorybackups$startDrops(ServerLevel level, DamageSource source, CallbackInfo info) {
        if ((Object) this instanceof ServerPlayer player) {
            DeathDrops.start(player);
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
    private void inventorybackups$finishDrops(ServerLevel level, DamageSource source, CallbackInfo info) {
        if ((Object) this instanceof ServerPlayer player) {
            DeathDrops.finish(player);
        }
    }
}
