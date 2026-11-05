package com.pockyl.inventorybackups.forge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import com.pockyl.inventorybackups.client.KeyBindings;
import com.pockyl.inventorybackups.network.Payloads;
import com.pockyl.inventorybackups.platform.ClientPlatform;

import java.util.List;

/**
 * {@link ClientPlatform} on Forge: the key has a real Ctrl modifier, so it never fires without it.
 */
public final class ForgeClientPlatform implements ClientPlatform {
    @Override
    public boolean hasChannel() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && ForgeNetwork.present(connection.getConnection());
    }

    @Override
    public void sendToServer(Payloads.Payload payload) {
        ForgeNetwork.sendToServer(payload);
    }

    @Override
    public KeyMapping createOpenKey() {
        return new KeyMapping(KeyBindings.OPEN_NAME, KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, KeyBindings.CATEGORY);
    }

    @Override
    public boolean openKeyModifierDown() {
        return true;
    }

    @Override
    public boolean openKeyMatches(int keyCode, int scanCode) {
        return KeyBindings.OPEN.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode));
    }

    @Override
    public void renderItemTooltip(GuiGraphics graphics, Font font, List<Component> lines, ItemStack stack, int x, int y) {
        graphics.renderTooltip(font, lines, stack.getTooltipImage(), stack, x, y);
    }
}
