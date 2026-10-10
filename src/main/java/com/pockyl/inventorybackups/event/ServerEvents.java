package com.pockyl.inventorybackups.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.command.BackupCommands;

/**
 * Registers the chat commands.
 */
@EventBusSubscriber(modid = InventoryBackups.MOD_ID)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BackupCommands.register(event.getDispatcher());
    }
}
