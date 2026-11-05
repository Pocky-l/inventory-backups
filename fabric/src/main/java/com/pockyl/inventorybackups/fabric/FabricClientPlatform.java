package com.pockyl.inventorybackups.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import com.pockyl.inventorybackups.client.KeyBindings;
import com.pockyl.inventorybackups.platform.ClientPlatform;

import java.util.List;

/**
 * {@link ClientPlatform} on Fabric. Key mappings have no modifiers here: the key is I, and Ctrl must be held with it.
 */
public final class FabricClientPlatform implements ClientPlatform {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(KeyBindings.CATEGORY);

    @Override
    public boolean hasChannel(CustomPacketPayload.Type<?> type) {
        return ClientPlayNetworking.canSend(type);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public KeyMapping createOpenKey() {
        return new KeyMapping(KeyBindings.OPEN_NAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY);
    }

    @Override
    public boolean openKeyModifierDown() {
        return Minecraft.getInstance().hasControlDown();
    }

    @Override
    public boolean openKeyMatches(int keyCode, int scanCode) {
        return KeyBindings.OPEN.matches(new KeyEvent(keyCode, scanCode, 0)) && Minecraft.getInstance().hasControlDown();
    }

    @Override
    public void renderItemTooltip(GuiGraphics graphics, Font font, List<Component> lines, ItemStack stack, int x, int y) {
        graphics.setTooltipForNextFrame(font, lines, stack.getTooltipImage(), x, y);
    }
}
