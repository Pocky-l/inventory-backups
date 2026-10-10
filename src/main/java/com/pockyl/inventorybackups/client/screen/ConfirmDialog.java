package com.pockyl.inventorybackups.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * A modal question in front of the screen. Enter picks the last (primary) choice, Escape cancels.
 */
final class ConfirmDialog {
    private static final int WIDTH = 260;

    record Choice(Component label, UiButton.Style style, Runnable action) {
    }

    private final AdminScreen screen;
    private final Component title;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private final List<Choice> choices;
    private final List<UiButton> buttons = new ArrayList<>();
    private final int x;
    private final int y;
    private final int h;

    ConfirmDialog(AdminScreen screen, Component title, List<Component> body, List<Choice> choices) {
        this.screen = screen;
        this.title = title;
        this.choices = choices;
        Font font = screen.mc().font;
        for (Component paragraph : body) {
            lines.addAll(font.split(paragraph, WIDTH - 16));
            lines.add(FormattedCharSequence.EMPTY);
        }
        h = 8 + 12 + lines.size() * 10 + 20;
        x = (screen.vw - WIDTH) / 2;
        y = (screen.vh - h) / 2;
        int bx = x + WIDTH - 8;
        for (int i = choices.size() - 1; i >= 0; i--) {
            Choice choice = choices.get(i);
            int bw = font.width(choice.label()) + 14;
            bx -= bw;
            buttons.add(0, new UiButton(bx, y + h - 22, bw, 15, choice.label(), choice.style(), () -> choose(choice)));
            bx -= 4;
        }
    }

    private void choose(Choice choice) {
        screen.closeDialog();
        choice.action().run();
    }

    void confirm() {
        choose(choices.get(choices.size() - 1));
    }

    void render(GuiGraphics g, int mx, int my) {
        Font font = screen.mc().font;
        g.fill(0, 0, screen.vw, screen.vh, Theme.SHADE);
        Theme.box(g, x, y, WIDTH, h, Theme.PANEL, Theme.LINE_2);
        Theme.outline(g, x - 1, y - 1, WIDTH + 2, h + 2, 0xFF000000);
        Theme.text(g, font, title.copy().withStyle(style -> style.withBold(true)), x + 8, y + 8, Theme.TEXT);
        int ly = y + 22;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 8, ly, Theme.MUTED, false);
            ly += 10;
        }
        for (UiButton button : buttons) {
            button.render(g, font, mx, my);
        }
    }

    void mouseClicked(double mx, double my, int button) {
        for (UiButton b : buttons) {
            if (b.click(mx, my)) {
                return;
            }
        }
        if (mx < x || mx >= x + WIDTH || my < y || my >= y + h) {
            screen.closeDialog();
        }
    }
}
