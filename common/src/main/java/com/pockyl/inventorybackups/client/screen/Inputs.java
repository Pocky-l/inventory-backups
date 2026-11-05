package com.pockyl.inventorybackups.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * The panels work with plain coordinates, key codes and characters; vanilla widgets take input events since 1.21.9.
 * The version-specific part of the input handling lives here.
 */
final class Inputs {
    private Inputs() {
    }

    static MouseButtonEvent click(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
    }

    static KeyEvent key(int keyCode, int scanCode, int modifiers) {
        return new KeyEvent(keyCode, scanCode, modifiers);
    }

    static CharacterEvent character(int codePoint, int modifiers) {
        return new CharacterEvent(codePoint);
    }

    static boolean shiftDown() {
        return Minecraft.getInstance().hasShiftDown();
    }
}
