package com.pockyl.inventorybackups.platform;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.pockyl.inventorybackups.network.Payloads;

import java.util.List;

/**
 * What the client part needs from the mod loader. Each loader project provides one implementation, listed in its
 * {@code META-INF/services}; it is only loaded on the client.
 */
public interface ClientPlatform {
    ClientPlatform INSTANCE = Platform.load(ClientPlatform.class);

    /** Whether the server has the mod's channel (has the mod). */
    boolean hasChannel();

    void sendToServer(Payloads.Payload payload);

    /** The key that opens the admin screen, Ctrl+I by default (names in {@code client.KeyBindings}). */
    KeyMapping createOpenKey();

    /** Whether the key that opens the admin screen was pressed with its modifier. */
    boolean openKeyModifierDown();

    /** Whether a key press in a screen is the key that opens the admin screen (which then closes it). */
    boolean openKeyMatches(int keyCode, int scanCode);

    /** The tooltip of a stack with the given lines and its tooltip image, styled by the mods that style tooltips. */
    void renderItemTooltip(GuiGraphics graphics, Font font, List<Component> lines, ItemStack stack, int x, int y);
}
