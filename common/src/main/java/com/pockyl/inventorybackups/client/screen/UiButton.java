package com.pockyl.inventorybackups.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * A flat button drawn in the screen's theme.
 */
final class UiButton {
    enum Style {
        NORMAL, PRIMARY, ACTIVE, GHOST, DANGER
    }

    int x;
    int y;
    int w;
    int h;
    Component label;
    Style style;
    boolean enabled = true;
    int textColor = Theme.TEXT;
    @Nullable
    Component tooltip;
    private final Runnable action;

    UiButton(int x, int y, int w, int h, Component label, Style style, Runnable action) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.label = label;
        this.style = style;
        this.action = action;
    }

    UiButton tooltip(Component text) {
        this.tooltip = text;
        return this;
    }

    UiButton color(int color) {
        this.textColor = color;
        return this;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    boolean click(double mouseX, double mouseY) {
        if (enabled && contains(mouseX, mouseY)) {
            action.run();
            return true;
        }
        return false;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        boolean hover = enabled && contains(mouseX, mouseY);
        int fill;
        int border;
        switch (style) {
            case PRIMARY -> {
                fill = Theme.PRIMARY_BG;
                border = Theme.ADDED;
            }
            case ACTIVE -> {
                fill = Theme.ACCENT_DIM;
                border = Theme.ACCENT;
            }
            case DANGER -> {
                fill = Theme.DANGER_BG;
                border = Theme.REMOVED;
            }
            case GHOST -> {
                fill = Theme.BG;
                border = Theme.LINE_2;
            }
            default -> {
                fill = Theme.PANEL_2;
                border = Theme.LINE_2;
            }
        }
        if (hover) {
            fill = brighter(fill);
            border = style == Style.NORMAL || style == Style.GHOST ? Theme.MUTED : border;
        }
        Theme.box(g, x, y, w, h, fill, border);
        int color = enabled ? textColor : Theme.FAINT;
        // Labels that do not fit (long translations, narrow windows) are cut instead of running over the border.
        FormattedCharSequence shown = Theme.ellipsize(font, label, w - 6);
        g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2, color, false);
    }

    private static int brighter(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 14);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 14);
        int b = Math.min(255, (color & 0xFF) + 14);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
