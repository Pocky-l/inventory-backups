package com.pockyl.inventorybackups.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import com.pockyl.inventorybackups.client.KeyBindings;
import com.pockyl.inventorybackups.network.Payloads;
import com.pockyl.inventorybackups.platform.ClientPlatform;

import java.util.List;

/**
 * {@link ClientPlatform} on Fabric. Key mappings have no modifiers here: the key is I, and Ctrl must be held with it.
 */
public final class FabricClientPlatform implements ClientPlatform {
    @Override
    public boolean hasChannel() {
        return FabricNetwork.canSendToServer();
    }

    @Override
    public void sendToServer(Payloads.Payload payload) {
        FabricNetwork.sendToServer(payload);
    }

    @Override
    public KeyMapping createOpenKey() {
        return new KeyMapping(KeyBindings.OPEN_NAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, KeyBindings.CATEGORY);
    }

    @Override
    public boolean openKeyModifierDown() {
        return Screen.hasControlDown();
    }

    @Override
    public boolean openKeyMatches(int keyCode, int scanCode) {
        return KeyBindings.OPEN.matches(keyCode, scanCode) && Screen.hasControlDown();
    }

    @Override
    public void renderItemTooltip(GuiGraphics graphics, Font font, List<Component> lines, ItemStack stack, int x, int y) {
        graphics.renderTooltip(font, lines, stack.getTooltipImage(), x, y);
    }
}
