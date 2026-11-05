package com.pockyl.inventorybackups.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.platform.ClientPlatform;

/**
 * The key that opens the admin screen, Ctrl+I by default. The loader makes the key mapping: NeoForge has key
 * modifiers, on Fabric the key is I and Ctrl is checked when it is pressed.
 */
public final class KeyBindings {
    public static final String OPEN_NAME = "key.inventorybackups.open";
    /** Shown as {@code key.category.inventorybackups.main}. */
    public static final Identifier CATEGORY = InventoryBackups.id("main");
    public static final KeyMapping OPEN = ClientPlatform.INSTANCE.createOpenKey();

    private KeyBindings() {
    }

    /** At the end of every client tick. */
    public static void onClientTick() {
        while (OPEN.consumeClick()) {
            if (!ClientPlatform.INSTANCE.openKeyModifierDown()) {
                continue;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (!ClientSession.requestOpen()) {
                minecraft.gui.hud.setOverlayMessage(Component.translatable("inventorybackups.ui.server_missing"), false);
            }
        }
    }
}
