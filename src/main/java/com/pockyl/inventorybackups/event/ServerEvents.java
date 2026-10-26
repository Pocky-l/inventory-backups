package com.pockyl.inventorybackups.event;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.command.BackupCommands;
import com.pockyl.inventorybackups.command.SearchCommands;

/**
 * Registers the chat commands.
 */
@Mod.EventBusSubscriber(modid = InventoryBackups.MOD_ID)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BackupCommands.register(event.getDispatcher());
        SearchCommands.register(event.getDispatcher());
    }
}
