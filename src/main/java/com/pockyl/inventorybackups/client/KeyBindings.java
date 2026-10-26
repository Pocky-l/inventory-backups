package com.pockyl.inventorybackups.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import com.pockyl.inventorybackups.InventoryBackups;

/**
 * The key that opens the admin screen, Ctrl+I by default.
 */
@Mod.EventBusSubscriber(modid = InventoryBackups.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class KeyBindings {
    public static final KeyMapping OPEN = new KeyMapping("key.inventorybackups.open", KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.inventorybackups");

    private KeyBindings() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
        // The key registration comes on the mod bus, client ticks on the game bus.
        MinecraftForge.EVENT_BUS.addListener(KeyBindings::onClientTick);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        while (OPEN.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (!ClientSession.requestOpen()) {
                minecraft.gui.setOverlayMessage(Component.translatable("inventorybackups.ui.server_missing"), false);
            }
        }
    }
}
