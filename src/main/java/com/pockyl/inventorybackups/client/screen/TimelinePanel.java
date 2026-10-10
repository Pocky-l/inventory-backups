package com.pockyl.inventorybackups.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.pockyl.inventorybackups.client.ClientConfig;
import com.pockyl.inventorybackups.client.Formats;
import com.pockyl.inventorybackups.network.EntryRow;
import com.pockyl.inventorybackups.storage.Trigger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The timeline: zoom presets, a track with a marker per entry over a density histogram, the A/B pins, the scrubber, time
 * ticks and the transport buttons. Clicking or dragging on the track selects the nearest entry; the mouse wheel zooms
 * around the cursor and Shift + wheel pans.
 */
final class TimelinePanel {
    private static final int HEIGHT = 66;
    private static final int DENSITY_BIN = 4;

    private final AdminScreen screen;
    private int x;
    private int y;
    private int w;
    private int trackX;
    private int trackY;
    private int trackW;
    private boolean dragging;
    private final List<UiButton> buttons = new ArrayList<>();
    private final List<UiButton> zooms = new ArrayList<>();

    TimelinePanel(AdminScreen screen) {
        this.screen = screen;
    }

    void layout() {
        x = AdminScreen.SIDEBAR;
        y = AdminScreen.TOP + 26;
        w = screen.vw - x;
        trackX = x + 8;
        trackY = y + 24;
        trackW = w - 16;
        Font font = screen.mc().font;
        zooms.clear();
        int zx = x + 8;
        for (ClientConfig.Zoom zoom : ClientConfig.Zoom.values()) {
            Component label = Component.translatable("inventorybackups.ui.zoom." + zoom.name().toLowerCase(Locale.ROOT));
            int bw = font.width(label) + 10;
            UiButton button = new UiButton(zx, y + 5, bw, 13, label, UiButton.Style.NORMAL, () -> screen.zoomTo(zoom));
            zooms.add(button);
            zx += bw - 1;
        }
        buttons.clear();
        int bx = x + 8;
        int by = y + 51;
        buttons.add(new UiButton(bx, by, 15, 13, Component.literal("«"), UiButton.Style.NORMAL, () -> screen.select(0))
                .tooltip(Component.translatable("inventorybackups.ui.first")));
        buttons.add(new UiButton(bx + 17, by, 15, 13, Component.literal("‹"), UiButton.Style.NORMAL, () -> screen.step(-1))
                .tooltip(Component.translatable("inventorybackups.ui.previous")));
        buttons.add(new UiButton(bx + 34, by, 15, 13, Component.literal("▶"), UiButton.Style.ACTIVE, screen::togglePlay)
                .tooltip(Component.translatable("inventorybackups.ui.play")));
        buttons.add(new UiButton(bx + 51, by, 15, 13, Component.literal("›"), UiButton.Style.NORMAL, () -> screen.step(1))
                .tooltip(Component.translatable("inventorybackups.ui.next")));
        buttons.add(new UiButton(bx + 68, by, 15, 13, Component.literal("»"), UiButton.Style.NORMAL,
                () -> screen.select(screen.model.entries.size() - 1)).tooltip(Component.translatable("inventorybackups.ui.last")));
        buttons.add(new UiButton(bx + 85, by, 15, 13, Component.literal("☠"), UiButton.Style.NORMAL,
                () -> screen.select(screen.model.previousDeath())).color(Theme.REMOVED)
                .tooltip(Component.translatable("inventorybackups.ui.death")));
        int right = x + w - 8;
        buttons.add(new UiButton(right - 30, by, 30, 13, Component.translatable("inventorybackups.ui.pins.clear"), UiButton.Style.GHOST,
                this::clearPins).tooltip(Component.translatable("inventorybackups.ui.pins.clear.hint")));
        buttons.add(new UiButton(right - 48, by, 16, 13, Component.literal("B"), UiButton.Style.NORMAL, () -> pin(false))
                .color(Theme.PIN_B).tooltip(Component.translatable("inventorybackups.ui.pin_b")));
        buttons.add(new UiButton(right - 66, by, 16, 13, Component.literal("A"), UiButton.Style.NORMAL, () -> pin(true))
                .color(Theme.PIN_A).tooltip(Component.translatable("inventorybackups.ui.pin_a")));
    }

    void pin(boolean a) {
        EntryRow row = screen.model.selected();
        if (row == null) {
            return;
        }
        if (a) {
            screen.model.pinA = row.time();
        } else {
            screen.model.pinB = row.time();
        }
        screen.requestState();
    }

    private void clearPins() {
        screen.model.pinA = null;
        screen.model.pinB = null;
        screen.model.base = TimelineModel.Base.PREVIOUS;
    }

    private int xOf(long time) {
        long span = Math.max(1, screen.model.to - screen.model.from);
        return trackX + 1 + (int) ((time - screen.model.from) * (trackW - 3) / span);
    }

    private long timeAt(double mouseX) {
        long span = screen.model.to - screen.model.from;
        return screen.model.from + (long) ((mouseX - trackX - 1) * span / Math.max(1, trackW - 3));
    }

    void render(GuiGraphics g, int mx, int my) {
        Font font = screen.mc().font;
        TimelineModel model = screen.model;
        g.fill(x, y, x + w, y + HEIGHT, Theme.PANEL);
        Theme.hline(g, x, y + HEIGHT - 1, w, Theme.LINE);

        long span = model.span;
        for (int i = 0; i < zooms.size(); i++) {
            UiButton zoom = zooms.get(i);
            zoom.style = TimelineModel.spanOf(ClientConfig.Zoom.values()[i]) == span ? UiButton.Style.ACTIVE : UiButton.Style.NORMAL;
            zoom.render(g, font, mx, my);
        }
        Component range = Component.literal(Formats.time(model.from) + " - " + Formats.time(model.to));
        Theme.textCentered(g, font, range, x + w / 2, y + 7, Theme.MUTED);
        renderLegend(g, font);

        Theme.box(g, trackX, trackY, trackW, 16, Theme.FIELD, Theme.LINE_2);
        g.enableScissor(sc(trackX + 1), sc(trackY - 10), sc(trackX + trackW - 1), sc(trackY + 16));
        renderDensity(g);
        EntryRow hovered = null;
        for (EntryRow row : model.entries) {
            int ex = xOf(row.time());
            boolean big = row.trigger() == Trigger.DEATH || row.trigger() == Trigger.RESTORE;
            int color = Theme.color(row.trigger());
            if (big) {
                g.fill(ex - 1, trackY + 2, ex + 2, trackY + 14, color);
            } else {
                g.fill(ex, trackY + 3, ex + 1, trackY + 13, row.trigger() == Trigger.CHANGE ? (color & 0x00FFFFFF) | 0xB0000000 : color);
            }
            if (my >= trackY && my < trackY + 16 && Math.abs(mx - ex) <= 1) {
                hovered = row;
            }
        }
        renderPin(g, font, model.pinA, "A", Theme.PIN_A);
        renderPin(g, font, model.pinB, "B", Theme.PIN_B);
        EntryRow selected = model.selected();
        if (selected != null) {
            int sx = xOf(selected.time());
            g.fill(sx - 1, trackY - 2, sx + 2, trackY + 17, Theme.TEXT);
            g.fill(sx - 3, trackY - 3, sx + 4, trackY - 1, Theme.TEXT);
        }
        g.disableScissor();
        if (model.loading) {
            Theme.textCentered(g, font, Component.translatable("inventorybackups.ui.loading"), trackX + trackW / 2, trackY + 4,
                    Theme.FAINT);
        } else if (model.entries.isEmpty()) {
            Theme.textCentered(g, font, Component.translatable("inventorybackups.ui.no_entries"), trackX + trackW / 2, trackY + 4,
                    Theme.FAINT);
        }

        renderTicks(g, font);

        buttons.get(2).label = Component.literal(screen.playing() ? "❚❚" : "▶");
        for (UiButton button : buttons) {
            button.render(g, font, mx, my);
            if (button.tooltip != null && button.contains(mx, my)) {
                screen.showTooltip(List.of(button.tooltip), ItemStack.EMPTY);
            }
        }
        if (selected != null) {
            Component now = Component.literal(Formats.time(selected.time()) + " ").withStyle(style -> style.withColor(Theme.TEXT)
                    .withBold(true))
                    .append(Component.translatable("inventorybackups.trigger." + selected.trigger().key())
                            .withStyle(style -> style.withColor(Theme.color(selected.trigger())).withBold(false)))
                    .append(Component.translatable("inventorybackups.ui.entry_of", model.index + 1, model.entries.size())
                            .withStyle(style -> style.withColor(Theme.MUTED).withBold(false)));
            int left = x + 8 + 102;
            int right = x + w - 8 - 70;
            Theme.text(g, font, now, Math.max(left, (left + right) / 2 - font.width(now) / 2), y + 54, right - left, Theme.TEXT);
        }
        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            int triggerColor = Theme.color(hovered.trigger());
            lines.add(Component.translatable("inventorybackups.trigger." + hovered.trigger().key())
                    .withStyle(style -> style.withColor(triggerColor)));
            lines.add(Component.literal(Formats.full(hovered.time())));
            if (!hovered.detail().isEmpty() && hovered.trigger() != Trigger.RESTORE) {
                lines.add(Component.literal(hovered.detail()).withStyle(style -> style.withColor(Theme.MUTED)));
            }
            lines.add(Component.translatable("inventorybackups.entry.changed", hovered.changed())
                    .withStyle(style -> style.withColor(Theme.FAINT)));
            screen.showTooltip(lines, ItemStack.EMPTY);
        } else if (mx >= trackX && mx < trackX + trackW && my >= trackY && my < trackY + 16) {
            screen.showTooltip(List.of(Component.literal(Formats.full(timeAt(mx))).withStyle(style -> style.withColor(Theme.MUTED))),
                    ItemStack.EMPTY);
        }
    }

    private void renderLegend(GuiGraphics g, Font font) {
        int lx = x + w - 8;
        Trigger[] shown = {Trigger.RESTORE, Trigger.CONTAINER, Trigger.DEATH, Trigger.CHANGE};
        for (Trigger trigger : shown) {
            Component label = Component.translatable("inventorybackups.ui.legend." + trigger.key());
            lx -= font.width(label);
            Theme.text(g, font, label, lx, y + 7, Theme.FAINT);
            lx -= 7;
            g.fill(lx, y + 9, lx + 5, y + 14, Theme.color(trigger));
            lx -= 6;
        }
    }

    private void renderDensity(GuiGraphics g) {
        int bins = Math.max(1, (trackW - 2) / DENSITY_BIN);
        int[] counts = new int[bins];
        int max = 1;
        for (EntryRow row : screen.model.entries) {
            int bin = (xOf(row.time()) - trackX - 1) / DENSITY_BIN;
            if (bin >= 0 && bin < bins) {
                counts[bin]++;
                max = Math.max(max, counts[bin]);
            }
        }
        for (int i = 0; i < bins; i++) {
            if (counts[i] > 0) {
                int h = Math.max(1, counts[i] * 12 / max);
                g.fill(trackX + 1 + i * DENSITY_BIN, trackY + 15 - h, trackX + 1 + i * DENSITY_BIN + DENSITY_BIN - 1, trackY + 15,
                        Theme.DENSITY);
            }
        }
    }

    private void renderPin(GuiGraphics g, Font font, Long time, String label, int color) {
        if (time == null || time < screen.model.from || time > screen.model.to) {
            return;
        }
        int px = xOf(time);
        g.fill(px, trackY - 1, px + 1, trackY + 16, color);
        g.fill(px - 4, trackY - 9, px + 5, trackY, color);
        Theme.text(g, font, label, px - 2, trackY - 8, 0xFF000000);
    }

    private void renderTicks(GuiGraphics g, Font font) {
        long span = Math.max(1, screen.model.to - screen.model.from);
        int count = Math.max(2, trackW / 110);
        for (int i = 0; i <= count; i++) {
            long time = screen.model.from + span * i / count;
            String label = Formats.tick(time, span);
            int tx = xOf(time);
            int lw = font.width(label);
            int left = i == 0 ? trackX : i == count ? trackX + trackW - lw : tx - lw / 2;
            Theme.text(g, font, label, left, y + 42, Theme.FAINT);
        }
    }

    private int sc(int virtual) {
        return Math.round(virtual * screen.scale);
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (my < y || my >= y + HEIGHT || mx < x) {
            return false;
        }
        for (UiButton zoom : zooms) {
            if (zoom.click(mx, my)) {
                return true;
            }
        }
        for (UiButton b : buttons) {
            if (b.click(mx, my)) {
                return true;
            }
        }
        if (mx >= trackX && mx < trackX + trackW && my >= trackY - 10 && my < trackY + 18) {
            dragging = true;
            screen.select(screen.model.nearest(timeAt(mx)));
        }
        return true;
    }

    void mouseDragged(double mx, double my) {
        if (dragging) {
            screen.select(screen.model.nearest(timeAt(Math.max(trackX, Math.min(trackX + trackW, mx)))));
        }
    }

    void mouseReleased() {
        dragging = false;
    }

    boolean mouseScrolled(double mx, double my, double amount) {
        if (my < y || my >= y + HEIGHT || mx < x) {
            return false;
        }
        if (Screen.hasShiftDown()) {
            screen.model.pan(-amount * 0.25);
        } else {
            long around = mx >= trackX && mx < trackX + trackW ? timeAt(mx) : screen.model.selectedTime();
            screen.model.zoom(amount > 0 ? 0.5 : 2.0, around);
        }
        screen.loadTimeline(screen.model.selectedTime());
        return true;
    }
}
