package com.pockyl.inventorybackups.fabric.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.inventorybackups.event.CommonEvents;

import java.util.OptionalInt;

/**
 * Deaths and container sessions of players, where NeoForge fires its events. A canceled death (Fabric
 * API's ALLOW_DEATH, totems) never reaches {@code die}.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    private ServerPlayer inventorybackups$self() {
        return (ServerPlayer) (Object) this;
    }

    // Before the drops and before mods that hook the death further down move items away.
    @Inject(method = "die", at = @At("HEAD"))
    private void inventorybackups$died(DamageSource cause, CallbackInfo info) {
        CommonEvents.beforeDeath(inventorybackups$self());
        CommonEvents.death(inventorybackups$self(), cause);
    }

    @Inject(method = "openMenu", at = @At("RETURN"))
    private void inventorybackups$openedMenu(MenuProvider menu, CallbackInfoReturnable<OptionalInt> info) {
        if (info.getReturnValue().isPresent()) {
            CommonEvents.containerOpened(inventorybackups$self(), inventorybackups$self().containerMenu);
        }
    }

    @Inject(method = "openHorseInventory", at = @At("TAIL"))
    private void inventorybackups$openedHorse(AbstractHorse horse, Container inventory, CallbackInfo info) {
        CommonEvents.containerOpened(inventorybackups$self(), inventorybackups$self().containerMenu);
    }

    @Inject(method = "openNautilusInventory", at = @At("TAIL"))
    private void inventorybackups$openedNautilus(AbstractNautilus nautilus, Container inventory, CallbackInfo info) {
        CommonEvents.containerOpened(inventorybackups$self(), inventorybackups$self().containerMenu);
    }

    // After the menu gave back what it held (crafting grid), before the player's own inventory menu takes over.
    @Inject(method = "doCloseContainer", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/server/level/ServerPlayer;containerMenu:Lnet/minecraft/world/inventory/AbstractContainerMenu;"))
    private void inventorybackups$closedMenu(CallbackInfo info) {
        CommonEvents.containerClosed(inventorybackups$self(), inventorybackups$self().containerMenu);
    }
}
