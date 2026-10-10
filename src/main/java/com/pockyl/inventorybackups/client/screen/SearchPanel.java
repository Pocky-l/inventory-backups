package com.pockyl.inventorybackups.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.pockyl.inventorybackups.client.Formats;
import com.pockyl.inventorybackups.network.Payloads;
import com.pockyl.inventorybackups.network.SearchRows;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The item search: a query with item completion, modes (item, name, enchantment, unique stack), a period and an
 * optional player; results in three columns: matching items, holders with their time ranges, and the transfer trail
 * as a chain. Clicking a holder or a transfer opens that moment on the timeline.
 */
final class SearchPanel {
    private static final int BAR = 44;
    private static final int SUGGESTIONS = 6;
    private static final long[] PERIODS = {86_400_000L, 7 * 86_400_000L, 30 * 86_400_000L, 0};
    private static final String[] PERIOD_KEYS = {"24h", "7d", "30d", "all"};

    private enum Mode {
        ITEM, NAME, ENCHANTMENT, UNIQUE
    }

    private final AdminScreen screen;
    private EditBox field;
    private Mode mode = Mode.ITEM;
    private int period = 1;
    private boolean onlyCurrentPlayer;
    private List<String> suggestions = List.of();
    private int suggestion;
    private boolean showSuggestions;
    @Nullable
    private Payloads.SearchResponse result;
    private boolean searching;
    private int itemsScroll;
    private int holdersScroll;
    private int trailScroll;
    private int y;
    private int bottom;
    private int itemsWidth;
    private int holdersWidth;

    SearchPanel(AdminScreen screen) {
        this.screen = screen;
    }

    void layout() {
        String text = field == null ? "" : field.getValue();
        y = AdminScreen.TOP;
        bottom = screen.vh - AdminScreen.STATUS;
        field = new EditBox(screen.mc().font, 24, y + 10, 200, 9, Component.translatable("inventorybackups.ui.search.query"));
        field.setBordered(false);
        field.setMaxLength(64);
        field.setValue(text);
        field.setTextColor(Theme.TEXT);
        field.setHint(Component.translatable("inventorybackups.ui.search.hint").withStyle(style -> style.withColor(Theme.FAINT)));
        itemsWidth = 170;
        holdersWidth = Math.max(220, (screen.vw - itemsWidth) * 45 / 100);
    }

    void focus() {
        field.setFocused(true);
    }

    boolean isTyping() {
        return field.isFocused();
    }

    void setResult(Payloads.SearchResponse response) {
        result = response;
        searching = false;
        itemsScroll = 0;
        holdersScroll = 0;
        trailScroll = 0;
    }

    void searchFailed() {
        searching = false;
    }

    private void updateSuggestions() {
        String text = field.getValue().trim().toLowerCase(Locale.ROOT);
        suggestion = 0;
        if (text.isEmpty() || mode == Mode.NAME || mode == Mode.UNIQUE) {
            suggestions = List.of();
            return;
        }
        Iterable<ResourceLocation> ids = mode == Mode.ENCHANTMENT && screen.mc().level != null
                ? screen.mc().level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).keySet()
                : BuiltInRegistries.ITEM.keySet();
        List<String> found = new ArrayList<>();
        for (ResourceLocation id : ids) {
            String full = id.toString();
            if (id.getPath().contains(text) || full.startsWith(text)) {
                found.add(full);
            }
        }
        found.sort(Comparator.comparing((String id) -> !idPath(id).startsWith(text)).thenComparing(String::length).thenComparing(id -> id));
        suggestions = found.size() > SUGGESTIONS ? found.subList(0, SUGGESTIONS) : found;
        showSuggestions = !suggestions.isEmpty();
    }

    private static String idPath(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    private void submit() {
        String text = field.getValue().trim();
        if (showSuggestions && !suggestions.isEmpty()) {
            text = suggestions.get(Math.min(suggestion, suggestions.size() - 1));
            field.setValue(text);
        }
        showSuggestions = false;
        if (text.isEmpty()) {
            return;
        }
        String query = switch (mode) {
            case ITEM -> text;
            case NAME -> "name:" + text;
            case ENCHANTMENT -> "ench:" + text;
            case UNIQUE -> text.startsWith("#") ? text : "#" + text;
        };
        run(query);
    }

    private void run(String query) {
        UUID player = onlyCurrentPlayer && screen.model.player != null ? screen.model.player.uuid() : null;
        searching = true;
        screen.runSearch(query, player, PERIODS[period], 0);
    }

    void render(GuiGraphics g, int mx, int my) {
        Font font = screen.mc().font;
        int vw = screen.vw;
        g.fill(0, y, vw, y + BAR, Theme.PANEL);
        Theme.hline(g, 0, y + BAR - 1, vw, Theme.LINE);
        Theme.box(g, 8, y + 6, 220, 16, Theme.FIELD, field.isFocused() ? Theme.ACCENT : Theme.LINE_2);
        Theme.text(g, font, Component.literal("⌕"), 12, y + 10, Theme.MUTED);
        field.render(g, mx, my, 0);

        int cx = 236;
        for (Mode option : Mode.values()) {
            cx += chip(g, font, cx, y + 8, Component.translatable("inventorybackups.ui.search.mode." + option.name()
                    .toLowerCase(Locale.ROOT)),
                    mode == option) + 3;
        }
        cx += 12;
        for (int i = 0; i < PERIODS.length; i++) {
            cx += chip(g, font, cx, y + 8, Component.translatable("inventorybackups.ui.search.period." + PERIOD_KEYS[i]), period == i) + 3;
        }
        cx += 12;
        Component who = onlyCurrentPlayer && screen.model.player != null
                ? Component.translatable("inventorybackups.ui.search.player", screen.model.player.name())
                : Component.translatable("inventorybackups.ui.search.anyone");
        chip(g, font, cx, y + 8, who, onlyCurrentPlayer);
        Theme.text(g, font, summary(), 8, y + 29, vw - 16, Theme.FAINT);

        int columnTop = y + BAR;
        renderItems(g, font, 0, columnTop, itemsWidth, mx, my);
        renderHolders(g, font, itemsWidth, columnTop, holdersWidth, mx, my);
        renderTrail(g, font, itemsWidth + holdersWidth, columnTop, vw - itemsWidth - holdersWidth, mx, my);

        if (showSuggestions && field.isFocused() && !suggestions.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            int sy = y + 23;
            Theme.box(g, 8, sy, 220, suggestions.size() * 18 + 2, Theme.FIELD, Theme.ACCENT);
            for (int i = 0; i < suggestions.size(); i++) {
                String id = suggestions.get(i);
                int ry = sy + 1 + i * 18;
                boolean on = i == suggestion || mx >= 8 && mx < 228 && my >= ry && my < ry + 18;
                if (on) {
                    g.fill(9, ry, 227, ry + 18, Theme.ACCENT_DIM);
                }
                if (mode == Mode.ITEM) {
                    BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(id)).ifPresent(item -> g.renderItem(new ItemStack(item),
                            11, ry + 1));
                }
                Theme.text(g, font, Component.literal(idPath(id)), 31, ry + 5, 150, on ? Theme.TEXT : Theme.MUTED);
                int colon = id.indexOf(':');
                Theme.textRight(g, font, Component.literal(colon < 0 ? "" : id.substring(0, colon)), 224, ry + 5, Theme.FAINT);
            }
            g.pose().popPose();
        }
    }

    private Component summary() {
        if (searching) {
            return Component.translatable("inventorybackups.ui.search.searching");
        }
        if (result == null) {
            return Component.translatable("inventorybackups.ui.search.intro");
        }
        long now = result.holders().stream().filter(SearchRows.HolderRow::holdsNow).count();
        return Component.translatable(result.truncated() ? "inventorybackups.ui.search.summary_more" : "inventorybackups.ui.search.summary",
                result.query(), result.holders().size(), now, result.trail().size());
    }

    private int chip(GuiGraphics g, Font font, int cx, int cy, Component label, boolean on) {
        int w = font.width(label) + 10;
        Theme.box(g, cx, cy, w, 13, on ? Theme.ACCENT_DIM : Theme.PANEL_2, on ? Theme.ACCENT : Theme.LINE);
        Theme.text(g, font, label, cx + 5, cy + 3, on ? Theme.TEXT : Theme.MUTED);
        return w;
    }

    private void header(GuiGraphics g, Font font, int x, int top, int w, Component title) {
        Theme.text(g, font, title, x + 8, top + 4, w - 16, Theme.MUTED);
        Theme.hline(g, x, top + 15, w, Theme.LINE);
        Theme.vline(g, x + w - 1, top, bottom - top, Theme.LINE);
    }

    private void renderItems(GuiGraphics g, Font font, int x, int top, int w, int mx, int my) {
        header(g, font, x, top, w, Component.translatable("inventorybackups.ui.search.items"));
        if (result == null) {
            return;
        }
        int listTop = top + 16;
        g.enableScissor(sc(x), sc(listTop), sc(x + w - 1), sc(bottom));
        int ry = listTop - itemsScroll;
        for (SearchRows.ItemRow item : result.items()) {
            boolean hover = mx >= x && mx < x + w - 1 && my >= ry && my < ry + 26 && my >= listTop;
            boolean traced = !item.fingerprint().isEmpty() && result.query().equals("#" + item.fingerprint());
            if (hover || traced) {
                g.fill(x, ry, x + w - 1, ry + 26, Theme.PANEL_2);
            }
            if (traced) {
                g.fill(x, ry, x + 2, ry + 26, Theme.ACCENT);
            }
            // The row for an item id stands for every stack of it, so it shows the plain item, not the stored sample.
            ItemStack shown = item.fingerprint().isEmpty() && !item.sample().isEmpty() ? new ItemStack(item.sample().getItem())
                    : item.sample();
            g.renderItem(shown, x + 6, ry + 5);
            Component name = shown.isEmpty() ? Component.literal(item.itemId()) : shown.getHoverName();
            Theme.text(g, font, name, x + 28, ry + 4, w - 34, item.fingerprint().isEmpty() ? Theme.TEXT : Theme.NAME);
            String shortPrint = item.fingerprint().substring(0, Math.min(6, item.fingerprint().length()));
            Component sub = item.fingerprint().isEmpty() ? Component.translatable("inventorybackups.ui.search.all_stacks")
                    : Component.translatable("inventorybackups.ui.search.unique", shortPrint);
            Theme.text(g, font, sub, x + 28, ry + 14, w - 34, item.fingerprint().isEmpty() ? Theme.FAINT : Theme.PIN_B);
            Theme.hline(g, x, ry + 25, w - 1, Theme.ROW_LINE);
            if (hover && !shown.isEmpty()) {
                List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(screen.mc(), shown));
                if (!item.fingerprint().isEmpty()) {
                    lines.add(Component.translatable("inventorybackups.ui.search.trace_hint")
                            .withStyle(style -> style.withColor(Theme.FAINT)));
                }
                screen.showTooltip(lines, shown);
            }
            ry += 26;
        }
        g.disableScissor();
    }

    private void renderHolders(GuiGraphics g, Font font, int x, int top, int w, int mx, int my) {
        Component title = PERIODS[period] > 0
                ? Component.translatable("inventorybackups.ui.search.holders_period",
                        Component.translatable("inventorybackups.ui.search.period."
                + PERIOD_KEYS[period]))
                : Component.translatable("inventorybackups.ui.search.holders");
        header(g, font, x, top, w, title);
        if (result == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long start = PERIODS[period] > 0 ? now - PERIODS[period]
                : result.holders().stream().mapToLong(SearchRows.HolderRow::first).filter(t -> t > 0).min().orElse(now - 86_400_000L);
        int listTop = top + 16;
        g.enableScissor(sc(x), sc(listTop), sc(x + w - 1), sc(bottom));
        int ry = listTop - holdersScroll;
        boolean headerNow = false;
        boolean headerBefore = false;
        for (SearchRows.HolderRow holder : result.holders()) {
            if (holder.holdsNow() && !headerNow || !holder.holdsNow() && !headerBefore) {
                Theme.text(g, font, Component.translatable(holder.holdsNow() ? "inventorybackups.ui.search.now"
                        : "inventorybackups.ui.search.before"),
                        x + 8, ry + 4, Theme.FAINT);
                ry += 14;
                headerNow |= holder.holdsNow();
                headerBefore |= !holder.holdsNow();
            }
            boolean hover = mx >= x && mx < x + w - 1 && my >= ry && my < ry + 26 && my >= listTop;
            if (hover) {
                g.fill(x, ry, x + w - 1, ry + 26, Theme.PANEL_2);
            }
            screen.playerFace(g, holder.player(), x + 6, ry + 5);
            Theme.text(g, font, Component.literal(holder.name()), x + 28, ry + 4, 90, Theme.TEXT);
            Component sub = holder.holdsNow() ? Component.translatable("inventorybackups.ui.search.since", Formats.time(holder.first()))
                    : Component.translatable("inventorybackups.ui.search.until", Formats.time(holder.last()));
            Theme.text(g, font, sub, x + 28 + Math.min(90, font.width(holder.name())) + 5, ry + 4, w - 150, Theme.FAINT);
            Component count = holder.holdsNow() ? Component.literal("×" + holder.count())
                    : Component.translatable("inventorybackups.ui.search.max", holder.max());
            Theme.textRight(g, font, count, x + w - 8, ry + 4, holder.holdsNow() ? Theme.TEXT : Theme.FAINT);
            int barX = x + 28;
            int barW = w - 36;
            g.fill(barX, ry + 16, barX + barW, ry + 20, Theme.FIELD);
            long[] ranges = holder.ranges();
            for (int i = 0; i + 1 < ranges.length; i += 2) {
                long from = Math.max(start, ranges[i]);
                long to = Math.min(now, ranges[i + 1] == Long.MAX_VALUE ? now : ranges[i + 1]);
                if (to < start || from > now) {
                    continue;
                }
                int a = barX + (int) ((from - start) * barW / Math.max(1, now - start));
                int b = barX + (int) ((to - start) * barW / Math.max(1, now - start));
                g.fill(a, ry + 16, Math.max(a + 1, b), ry + 20, ranges[i + 1] == Long.MAX_VALUE ? Theme.ADDED : Theme.MUTED);
            }
            Theme.hline(g, x, ry + 25, w - 1, Theme.ROW_LINE);
            if (hover) {
                screen.showTooltip(List.of(Component.literal(holder.name()),
                        Component.translatable("inventorybackups.ui.search.seen", Formats.time(holder.first()), Formats.time(holder.last()))
                                .withStyle(style -> style.withColor(Theme.MUTED)),
                        Component.translatable("inventorybackups.ui.search.open_hint").withStyle(style -> style.withColor(Theme.FAINT))),
                        ItemStack.EMPTY);
            }
            ry += 26;
        }
        g.disableScissor();
    }

    /** A step of the chain: a player node, or the link that leads to the next one. */
    private record ChainStep(@Nullable SearchRows.LinkRow link, UUID player, String name, long time) {
    }

    private List<ChainStep> chain() {
        List<ChainStep> steps = new ArrayList<>();
        if (result == null) {
            return steps;
        }
        List<SearchRows.LinkRow> links = new ArrayList<>(result.trail());
        links.sort(Comparator.comparingLong(SearchRows.LinkRow::time));
        UUID last = null;
        for (SearchRows.LinkRow link : links) {
            if (link.from() != null && !link.from().equals(last)) {
                steps.add(new ChainStep(null, link.from(), link.fromName(), link.time()));
            }
            steps.add(new ChainStep(link, link.to(), link.toName(), link.time()));
            last = link.to();
        }
        return steps;
    }

    private void renderTrail(GuiGraphics g, Font font, int x, int top, int w, int mx, int my) {
        header(g, font, x, top, w, Component.translatable("inventorybackups.ui.search.trail"));
        if (result == null) {
            return;
        }
        int listTop = top + 16;
        List<ChainStep> steps = chain();
        if (steps.isEmpty()) {
            Theme.text(g, font, Component.translatable("inventorybackups.ui.search.no_trail"), x + 8, listTop + 6, w - 16, Theme.FAINT);
            return;
        }
        g.enableScissor(sc(x), sc(listTop), sc(x + w - 1), sc(bottom));
        int ry = listTop + 6 - trailScroll;
        for (ChainStep step : steps) {
            SearchRows.LinkRow link = step.link();
            if (link != null) {
                boolean exact = link.kind().exact();
                int color = exact ? Theme.ADDED : Theme.CHANGED;
                for (int dy = 0; dy < 24; dy++) {
                    if (exact || dy % 4 < 2) {
                        g.fill(x + 15, ry + dy, x + 17, ry + dy + 1, color);
                    }
                }
                boolean hover = mx >= x && mx < x + w - 1 && my >= ry && my < ry + 24 && my >= listTop;
                Theme.text(g, font, how(link), x + 24, ry + 3, w - 32, hover ? Theme.TEXT : color);
                Theme.text(g, font, Component.literal(Formats.time(link.time()) + " · " + Formats.place(link.dimension(), link.pos().getX(),
                        link.pos().getY(), link.pos().getZ())), x + 24, ry + 13, w - 32, Theme.FAINT);
                if (hover) {
                    screen.showTooltip(List.of(Component.translatable("inventorybackups.ui.search.open_hint")), ItemStack.EMPTY);
                }
                ry += 24;
            }
            boolean hover = mx >= x && mx < x + w - 1 && my >= ry && my < ry + 18 && my >= listTop;
            screen.playerFace(g, step.player(), x + 8, ry + 1);
            Theme.text(g, font, Component.literal(step.name().isEmpty() ? step.player().toString() : step.name()), x + 30, ry + 5, w - 120,
                    hover ? Theme.ACCENT : Theme.TEXT);
            Theme.textRight(g, font, Component.literal(Formats.time(step.time())), x + w - 8, ry + 5, Theme.FAINT);
            ry += 18;
        }
        g.disableScissor();
    }

    private static Component how(SearchRows.LinkRow link) {
        Component count = Component.literal(link.count() > 0 ? "×" + link.count() + " " : "");
        return switch (link.kind()) {
            case DROP -> Component.translatable("inventorybackups.ui.search.link.drop", count);
            case DEATH_LOOT -> Component.translatable("inventorybackups.ui.search.link.death", count);
            case CONTAINER -> Component.translatable("inventorybackups.ui.search.link.container", count,
                    link.container().isEmpty() ? "?" : idPath(link.container()), position(link.containerPos()));
            case PROBABLE -> Component.translatable("inventorybackups.ui.search.link.probable", link.gapMillis() / 1000);
        };
    }

    private static String position(@Nullable BlockPos pos) {
        return pos == null ? "" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private int sc(int virtual) {
        return Math.round(virtual * screen.scale);
    }

    boolean mouseClicked(double mx, double my, int button) {
        Font font = screen.mc().font;
        if (showSuggestions && field.isFocused() && mx >= 8 && mx < 228 && my >= y + 24 && my < y + 24 + suggestions.size() * 18) {
            suggestion = (int) ((my - y - 24) / 18);
            submit();
            return true;
        }
        if (my >= y + 6 && my < y + 22 && mx >= 8 && mx < 228) {
            field.setFocused(true);
            field.mouseClicked(mx, my, button);
            return true;
        }
        field.setFocused(false);
        showSuggestions = false;
        if (my >= y + 8 && my < y + 21) {
            int cx = 236;
            for (Mode option : Mode.values()) {
                int w = font.width(Component.translatable("inventorybackups.ui.search.mode." + option.name().toLowerCase(Locale.ROOT)))
                        + 10;
                if (mx >= cx && mx < cx + w) {
                    mode = option;
                    updateSuggestions();
                    showSuggestions = false;
                    return true;
                }
                cx += w + 3;
            }
            cx += 12;
            for (int i = 0; i < PERIODS.length; i++) {
                int w = font.width(Component.translatable("inventorybackups.ui.search.period." + PERIOD_KEYS[i])) + 10;
                if (mx >= cx && mx < cx + w) {
                    period = i;
                    if (result != null) {
                        run(result.query());
                    }
                    return true;
                }
                cx += w + 3;
            }
            cx += 12;
            if (mx >= cx && screen.model.player != null) {
                onlyCurrentPlayer = !onlyCurrentPlayer;
                if (result != null) {
                    run(result.query());
                }
            }
            return true;
        }
        if (result == null || my < y + BAR + 16) {
            return true;
        }
        if (mx < itemsWidth) {
            int ry = y + BAR + 16 - itemsScroll;
            for (SearchRows.ItemRow item : result.items()) {
                if (my >= ry && my < ry + 26 && !item.fingerprint().isEmpty()) {
                    run("#" + item.fingerprint());
                    return true;
                }
                ry += 26;
            }
        } else if (mx < itemsWidth + holdersWidth) {
            int ry = y + BAR + 16 - holdersScroll;
            boolean headerNow = false;
            boolean headerBefore = false;
            for (SearchRows.HolderRow holder : result.holders()) {
                if (holder.holdsNow() && !headerNow || !holder.holdsNow() && !headerBefore) {
                    ry += 14;
                    headerNow |= holder.holdsNow();
                    headerBefore |= !holder.holdsNow();
                }
                if (my >= ry && my < ry + 26) {
                    screen.openTimeline(holder.player(), holder.name(), holder.holdsNow() ? holder.last() : holder.last() - 1);
                    return true;
                }
                ry += 26;
            }
        } else {
            int ry = y + BAR + 16 + 6 - trailScroll;
            for (ChainStep step : chain()) {
                if (step.link() != null) {
                    if (my >= ry && my < ry + 24) {
                        screen.openTimeline(step.player(), step.name(), step.link().time() + 1);
                        return true;
                    }
                    ry += 24;
                }
                if (my >= ry && my < ry + 18) {
                    screen.openTimeline(step.player(), step.name(), step.time() + 1);
                    return true;
                }
                ry += 18;
            }
        }
        return true;
    }

    boolean mouseScrolled(double mx, double my, double amount) {
        int step = (int) (amount * 26);
        if (mx < itemsWidth) {
            itemsScroll = Math.max(0, itemsScroll - step);
        } else if (mx < itemsWidth + holdersWidth) {
            holdersScroll = Math.max(0, holdersScroll - step);
        } else {
            trailScroll = Math.max(0, trailScroll - step);
        }
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!field.isFocused()) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                submit();
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                suggestion = Math.min(suggestions.size() - 1, suggestion + 1);
                showSuggestions = true;
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                suggestion = Math.max(0, suggestion - 1);
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (showSuggestions) {
                    showSuggestions = false;
                } else {
                    field.setFocused(false);
                }
                return true;
            }
            default -> {
                String before = field.getValue();
                boolean handled = field.keyPressed(keyCode, scanCode, modifiers);
                if (!before.equals(field.getValue())) {
                    updateSuggestions();
                }
                return handled;
            }
        }
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (!field.isFocused()) {
            return false;
        }
        boolean handled = field.charTyped(codePoint, modifiers);
        updateSuggestions();
        return handled;
    }
}
