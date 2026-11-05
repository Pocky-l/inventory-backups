package com.pockyl.inventorybackups.fabric.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.inventorybackups.event.CommonEvents;

/**
 * Joins and quits at the same points as on NeoForge: when the player is fully placed in the world, and before the
 * player is saved and removed.
 */
@Mixin(PlayerList.class)
abstract class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void inventorybackups$joined(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo info) {
        CommonEvents.beforeJoin(player);
        CommonEvents.playerJoined(player);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void inventorybackups$left(ServerPlayer player, CallbackInfo info) {
        CommonEvents.playerLeft(player);
    }
}
