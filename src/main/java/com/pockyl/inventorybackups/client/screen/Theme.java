package com.pockyl.inventorybackups.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import com.pockyl.inventorybackups.storage.Trigger;

import java.util.List;

/**
 * Colors and drawing helpers of the admin screen. The values are the ones of the HTML mockup in docs/ui-mockup.
 */
final class Theme {
    static final int BG = 0xFF101217;
    static final int TOPBAR = 0xFF0C0E12;
    static final int PANEL = 0xFF181B21;
    static final int PANEL_2 = 0xFF1F232B;
    static final int FIELD = 0xFF0E1014;
    static final int LINE = 0xFF2B3039;
    static final int LINE_2 = 0xFF363C47;
    static final int ROW_LINE = 0xFF1C2027;
    static final int TEXT = 0xFFE6E8EB;
    static final int MUTED = 0xFF8B93A1;
    static final int FAINT = 0xFF5C6370;
    static final int ACCENT = 0xFF5AA9FF;
    static final int ACCENT_DIM = 0xFF284A73;
    static final int SLOT = 0xFF262A33;
    static final int SLOT_HI = 0xFF323844;
    static final int SLOT_LO = 0xFF15181D;
    static final int SLOT_HOVER = 0xFF3B414D;
    static final int ADDED = 0xFF4FD17A;
    static final int ADDED_BG = 0xFF1D3B29;
    static final int REMOVED = 0xFFF05F5F;
    static final int REMOVED_BG = 0xFF3D1F22;
    static final int CHANGED = 0xFFF2C14E;
    static final int PIN_A = 0xFFFF8A3D;
    static final int PIN_B = 0xFFB48CFF;
    static final int PRIMARY_BG = 0xFF1F5C3A;
    static final int DANGER_BG = 0xFF5C2A2A;
    static final int DENSITY = 0xFF222833;
    static final int SHADE = 0xA0000000;
    static final int NAME = 0xFF55FFFF;

    private Theme() {
    }

    static int color(Trigger trigger) {
        return switch (trigger) {
            case CHANGE -> 0xFFE5C07B;
            case DEATH -> 0xFFEF5F5F;
            case JOIN -> 0xFF6FCF7F;
            case QUIT -> 0xFF8B93A1;
            case CONTAINER -> 0xFFC678DD;
            case RESTORE -> 0xFF56B6C2;
            case EDIT -> 0xFFF0A35E;
            case COMMAND -> 0xFF61AFEF;
            case IMPORT -> 0xFF3FB8A5;
        };
    }

    static void box(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    static void hline(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color);
    }

    static void vline(GuiGraphics g, int x, int y, int h, int color) {
        g.fill(x, y, x + 1, y + h, color);
    }

    static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    static void text(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    /** Text cut to the width, with an ellipsis when it does not fit. */
    static void text(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        g.drawString(font, ellipsize(font, text, width), x, y, color, false);
    }

    /**
     * Text wrapped to the width, at most {@code maxLines} lines (the last one cut with an ellipsis).
     *
     * @return the number of lines drawn
     */
    static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int maxLines, int color) {
        List<FormattedText> lines = font.getSplitter().splitLines(text, width, Style.EMPTY);
        int count = Math.min(lines.size(), maxLines);
        for (int i = 0; i < count; i++) {
            boolean cut = i == count - 1 && lines.size() > count;
            FormattedCharSequence line = cut
                    ? ellipsize(font, Component.literal(lines.get(i).getString() + " " + lines.get(i + 1).getString()), width)
                    : Language.getInstance().getVisualOrder(lines.get(i));
            g.drawString(font, line, x, y + i * 10, color, false);
        }
        return count;
    }

    /** Widths that fit {@code available}: the wanted ones when they fit, otherwise each cut by the same share. */
    static int[] fit(int[] wanted, int available) {
        int total = 0;
        for (int w : wanted) {
            total += w;
        }
        if (total <= available) {
            return wanted;
        }
        int[] fitted = new int[wanted.length];
        for (int i = 0; i < wanted.length; i++) {
            fitted[i] = Math.max(12, wanted[i] * available / total);
        }
        return fitted;
    }

    static void textRight(GuiGraphics g, Font font, Component text, int right, int y, int color) {
        g.drawString(font, text, right - font.width(text), y, color, false);
    }

    static void textCentered(GuiGraphics g, Font font, Component text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    static FormattedCharSequence ellipsize(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width("..")));
        return FormattedCharSequence.composite(Language.getInstance().getVisualOrder(cut), Component.literal("..").getVisualOrderText());
    }

    /** A slot with the inset look of the inventory screen. */
    static void slot(GuiGraphics g, int x, int y, int fill) {
        g.fill(x, y, x + 18, y + 18, fill);
        hline(g, x, y, 18, SLOT_LO);
        vline(g, x, y, 18, SLOT_LO);
        hline(g, x, y + 17, 18, SLOT_HI);
        vline(g, x + 17, y, 18, SLOT_HI);
    }

    static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.renderOutline(x, y, w, h, color);
    }
}
