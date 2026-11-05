package com.pockyl.inventorybackups.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.pockyl.inventorybackups.client.Formats;
import com.pockyl.inventorybackups.network.PlayerRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The player browser: a name filter, quick filters (all, online, died today) and the list, online players first,
 * then by last activity; players with a recent death carry a skull.
 */
final class PlayerListPanel {
    private static final int ROW = 22;
    private static final int GROUP = 13;
    private static final long RECENT_DEATH = 24 * TimelineModel.HOUR;

    private enum Filter {
        ALL, ONLINE, DIED
    }

    private final AdminScreen screen;
    private EditBox field;
    private List<PlayerRow> players = List.of();
    private Filter filter = Filter.ALL;
    private int scroll;
    private String lastQuery = "";
    private int top;
    private int bottom;

    PlayerListPanel(AdminScreen screen) {
        this.screen = screen;
    }

    void layout() {
        String text = field == null ? "" : field.getValue();
        field = new EditBox(screen.mc().font, 24, AdminScreen.TOP + 10, 126, 9,
                Component.translatable("inventorybackups.ui.players.search"));
        field.setBordered(false);
        field.setMaxLength(32);
        field.setValue(text);
        field.setHint(Component.translatable("inventorybackups.ui.players.search").withStyle(style -> style.withColor(Theme.FAINT)));
        field.setTextColor(Theme.TEXT);
        top = AdminScreen.TOP + 46;
        bottom = screen.vh - AdminScreen.STATUS;
    }

    void setPlayers(List<PlayerRow> rows) {
        players = rows;
        scroll = Math.min(scroll, maxScroll());
    }

    boolean isEmpty() {
        return players.isEmpty();
    }

    boolean isTyping() {
        return field.isFocused();
    }

    @Nullable
    PlayerRow find(UUID uuid) {
        for (PlayerRow row : players) {
            if (row.uuid().equals(uuid)) {
                return row;
            }
        }
        return null;
    }

    /** The rows to show, with group headers as null entries' labels. */
    private List<Object> visible() {
        List<Object> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        boolean online = false;
        boolean offline = false;
        for (PlayerRow row : players) {
            boolean died = row.lastDeath() > 0 && now - row.lastDeath() < RECENT_DEATH;
            if (filter == Filter.ONLINE && !row.online() || filter == Filter.DIED && !died) {
                continue;
            }
            if (row.online() && !online) {
                rows.add(Component.translatable("inventorybackups.ui.players.online"));
                online = true;
            } else if (!row.online() && !offline) {
                rows.add(Component.translatable("inventorybackups.ui.players.recent"));
                offline = true;
            }
            rows.add(row);
        }
        return rows;
    }

    private int contentHeight() {
        int height = 0;
        for (Object row : visible()) {
            height += row instanceof PlayerRow ? ROW : GROUP;
        }
        return height;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (bottom - top));
    }

    void render(GuiGraphics g, int mx, int my) {
        int x = 0;
        int y = AdminScreen.TOP;
        g.fill(x, y, AdminScreen.SIDEBAR - 1, bottom, Theme.PANEL);
        Theme.vline(g, AdminScreen.SIDEBAR - 1, y, bottom - y, Theme.LINE);
        Theme.box(g, 6, y + 6, 144, 16, Theme.FIELD, field.isFocused() ? Theme.ACCENT : Theme.LINE_2);
        Theme.text(g, screen.mc().font, Component.literal("⌕"), 10, y + 10, Theme.MUTED);
        field.render(g, mx, my, 0);

        int cx = 6;
        int[] widths = filterWidths();
        for (Filter option : Filter.values()) {
            Component label = filterLabel(option);
            int w = widths[option.ordinal()];
            boolean on = filter == option;
            boolean hover = mx >= cx && mx < cx + w && my >= y + 26 && my < y + 39;
            Theme.box(g, cx, y + 26, w, 13, on ? Theme.ACCENT_DIM : Theme.PANEL_2, on ? Theme.ACCENT : Theme.LINE);
            Theme.text(g, screen.mc().font, label, cx + 5, y + 29, w - 8, on || hover ? Theme.TEXT : Theme.MUTED);
            if (hover && option == Filter.DIED) {
                screen.showTooltip(List.of(Component.translatable("inventorybackups.ui.players.filter.died.hint")),
                        ItemStack.EMPTY);
            }
            cx += w + 3;
        }

        g.enableScissor(0, sc(top), sc(AdminScreen.SIDEBAR - 1), sc(bottom));
        int ry = top - scroll;
        UUID selected = screen.model.player == null ? null : screen.model.player.uuid();
        long now = System.currentTimeMillis();
        for (Object entry : visible()) {
            if (entry instanceof Component label) {
                Theme.text(g, screen.mc().font, label, 8, ry + 3, Theme.FAINT);
                ry += GROUP;
                continue;
            }
            PlayerRow row = (PlayerRow) entry;
            if (ry + ROW >= top && ry <= bottom) {
                boolean isSelected = row.uuid().equals(selected);
                boolean hover = mx >= 0 && mx < AdminScreen.SIDEBAR - 1 && my >= ry && my < ry + ROW && my >= top && my < bottom;
                if (isSelected || hover) {
                    g.fill(0, ry, AdminScreen.SIDEBAR - 1, ry + ROW, Theme.PANEL_2);
                }
                if (isSelected) {
                    g.fill(0, ry, 2, ry + ROW, Theme.ACCENT);
                }
                face(g, row.uuid(), 8, ry + 3);
                Theme.text(g, screen.mc().font, Component.literal(row.name()), 28, ry + 3, 110, Theme.TEXT);
                Component sub = row.online() ? Component.translatable("inventorybackups.ui.status.online")
                        : Component.translatable("inventorybackups.ui.seen", Formats.ago(row.lastSeen()));
                if (row.lastDeath() > 0 && now - row.lastDeath() < RECENT_DEATH) {
                    sub = Component.translatable("inventorybackups.ui.died", sub, Formats.time(row.lastDeath()));
                    Theme.text(g, screen.mc().font, Component.literal("☠"), AdminScreen.SIDEBAR - 14, ry + 3, Theme.REMOVED);
                }
                Theme.text(g, screen.mc().font, sub, 28, ry + 12, 120, row.online() ? Theme.ADDED : Theme.FAINT);
            }
            ry += ROW;
        }
        g.disableScissor();
    }

    private static Component filterLabel(Filter option) {
        return Component.translatable("inventorybackups.ui.players.filter." + option.name().toLowerCase(Locale.ROOT));
    }

    /** Widths of the quick filter buttons, shrunk to the width of the search field when the labels are long. */
    private int[] filterWidths() {
        Filter[] options = Filter.values();
        int[] widths = new int[options.length];
        for (int i = 0; i < options.length; i++) {
            widths[i] = screen.mc().font.width(filterLabel(options[i])) + 10;
        }
        return Theme.fit(widths, 144 - 3 * (options.length - 1));
    }

    /** Draws the face of a player: their real skin when they are online, the default skin for their UUID otherwise. */
    void face(GuiGraphics g, UUID uuid, int x, int y) {
        ClientPacketListener connection = screen.mc().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(uuid);
        PlayerFaceRenderer.draw(g, info != null ? info.getSkin() : DefaultPlayerSkin.get(uuid), x, y, 16);
    }

    private int sc(int virtual) {
        return Math.round(virtual * screen.scale);
    }

    boolean mouseClicked(double mx, double my, int button) {
        int y = AdminScreen.TOP;
        if (mx >= AdminScreen.SIDEBAR) {
            field.setFocused(false);
            return false;
        }
        if (my >= y + 6 && my < y + 22) {
            field.setFocused(true);
            field.mouseClicked(mx, my, button);
            return true;
        }
        field.setFocused(false);
        if (my >= y + 26 && my < y + 39) {
            int cx = 6;
            int[] widths = filterWidths();
            for (Filter option : Filter.values()) {
                int w = widths[option.ordinal()];
                if (mx >= cx && mx < cx + w) {
                    filter = option;
                    scroll = 0;
                    return true;
                }
                cx += w + 3;
            }
            return true;
        }
        if (my < top || my >= bottom) {
            return false;
        }
        int ry = top - scroll;
        for (Object entry : visible()) {
            int h = entry instanceof PlayerRow ? ROW : GROUP;
            if (entry instanceof PlayerRow row && my >= ry && my < ry + h) {
                screen.selectPlayer(row, Long.MIN_VALUE);
                return true;
            }
            ry += h;
        }
        return true;
    }

    boolean mouseScrolled(double mx, double my, double amount) {
        if (mx >= AdminScreen.SIDEBAR || my < top) {
            return false;
        }
        scroll = (int) Math.max(0, Math.min(maxScroll(), scroll - amount * ROW));
        return true;
    }

    /** Selects the next or previous player in the list. */
    void step(int delta) {
        List<PlayerRow> rows = new ArrayList<>();
        for (Object entry : visible()) {
            if (entry instanceof PlayerRow row) {
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        int index = screen.model.player == null ? -1 : rows.indexOf(find(screen.model.player.uuid()));
        int next = Math.max(0, Math.min(rows.size() - 1, index + delta));
        if (next != index) {
            screen.selectPlayer(rows.get(next), Long.MIN_VALUE);
        }
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!field.isFocused()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
            field.setFocused(false);
            return true;
        }
        boolean handled = field.keyPressed(keyCode, scanCode, modifiers);
        refilter();
        return handled || keyCode != GLFW.GLFW_KEY_ESCAPE;
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (!field.isFocused()) {
            return false;
        }
        boolean handled = field.charTyped(codePoint, modifiers);
        refilter();
        return handled;
    }

    private void refilter() {
        String query = field.getValue().trim();
        if (!query.equals(lastQuery)) {
            lastQuery = query;
            scroll = 0;
            screen.loadPlayers(query);
        }
    }
}
