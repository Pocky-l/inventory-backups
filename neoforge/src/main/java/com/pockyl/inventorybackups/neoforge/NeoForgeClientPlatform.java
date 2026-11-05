package com.pockyl.inventorybackups.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

import com.pockyl.inventorybackups.client.KeyBindings;
import com.pockyl.inventorybackups.platform.ClientPlatform;

import java.util.List;

/**
 * {@link ClientPlatform} on NeoForge: the key has a real Ctrl modifier, so it never fires without it.
 */
public final class NeoForgeClientPlatform implements ClientPlatform {
    /** Registered by {@link NeoForgeClient} with the key. */
    static final KeyMapping.Category CATEGORY = new KeyMapping.Category(KeyBindings.CATEGORY);

    @Override
    public boolean hasChannel(CustomPacketPayload.Type<?> type) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @Override
    public KeyMapping createOpenKey() {
        return new KeyMapping(KeyBindings.OPEN_NAME, KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY);
    }

    @Override
    public boolean openKeyModifierDown() {
        return true;
    }

    @Override
    public boolean openKeyMatches(int keyCode, int scanCode) {
        return KeyBindings.OPEN.isActiveAndMatches(InputConstants.getKey(new KeyEvent(keyCode, scanCode, 0)));
    }

    @Override
    public void renderItemTooltip(GuiGraphicsExtractor graphics, Font font, List<Component> lines, ItemStack stack, int x, int y) {
        graphics.setTooltipForNextFrame(font, lines, stack.getTooltipImage(), stack, x, y);
    }
}
