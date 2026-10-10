package com.pockyl.inventorybackups.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import com.pockyl.inventorybackups.InventoryBackups;

/**
 * The key that opens the admin screen, Ctrl+I by default.
 */
@EventBusSubscriber(modid = InventoryBackups.MOD_ID, value = Dist.CLIENT)
public final class KeyBindings {
    public static final KeyMapping OPEN = new KeyMapping("key.inventorybackups.open", KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.inventorybackups");

    private KeyBindings() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (!ClientSession.requestOpen()) {
                minecraft.gui.setOverlayMessage(Component.translatable("inventorybackups.ui.server_missing"), false);
            }
        }
    }
}
