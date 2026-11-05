package com.pockyl.inventorybackups.fabric;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.fml.config.ModConfig;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.event.CommonEvents;

/**
 * The Fabric entry point on both sides: the config (through Forge Config API Port, same file as on NeoForge), the
 * payloads and the game events. What Fabric API has no event for comes from the mixins in {@code fabric.mixin}: joins
 * and quits, deaths and death drops, tossed and picked up items, opened and closed containers.
 */
public final class InventoryBackupsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ForgeConfigRegistry.INSTANCE.register(InventoryBackups.MOD_ID, ModConfig.Type.COMMON, Config.SPEC, InventoryBackups.CONFIG_FILE);
        FabricNetwork.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> CommonEvents.registerCommands(dispatcher));
        // Started, not starting: on Fabric "starting" comes before the worlds are loaded.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CommonEvents.serverStarting(server);
            CommonEvents.serverStarted(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> CommonEvents.serverStopping());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CommonEvents.serverStopped());
        ServerTickEvents.END_SERVER_TICK.register(CommonEvents::serverTick);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> CommonEvents.playerRespawned(newPlayer));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                CommonEvents.rightClickBlock(serverPlayer, hit.getBlockPos());
            }
            return InteractionResult.PASS;
        });
    }
}
