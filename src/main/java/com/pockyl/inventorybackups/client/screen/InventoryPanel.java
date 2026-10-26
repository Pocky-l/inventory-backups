package com.pockyl.inventorybackups.client.screen;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.backup.Slots;
import com.pockyl.inventorybackups.client.ClientConfig;
import com.pockyl.inventorybackups.client.Formats;
import com.pockyl.inventorybackups.client.ModSlotNames;
import com.pockyl.inventorybackups.network.EntryRow;
import com.pockyl.inventorybackups.network.Payloads;

import java.util.ArrayList;
import java.util.List;

/**
 * The inventory at the selected moment in the layout of the player screen, with the ender chest and the slots of other
 * mods (Curios, Accessories, ...) on their own tabs. Slots are colored by how they differ from the compared state: green
 * added, red removed (the removed item shown faded), yellow with a badge for count changes. Clicking selects slots for restoring or copying them.
 */
final class InventoryPanel {
    static final int WIDTH = 190;

    enum Section {
        INVENTORY, ENDER_CHEST, MOD_SLOTS
    }

    /** A slot on screen. */
    private record Cell(int slot, int x, int y, @Nullable String hint) {
    }

    /** The name of a group of mod slots above its first row. */
    private record Header(Component name, int y) {
    }

    /** A tab above the slots, with the place it got after fitting all tabs into the panel. */
    private record Tab(Section section, Component label, int x, int w) {
    }

    /** Top and bottom of the scrolled area of the mod slots tab; the selected slot card follows below. */
    private int modTop() {
        return y + 24;
    }

    private int modBottom() {
        return y + 152;
    }

    private final AdminScreen screen;
    private int x;
    private int y;
    private int bottom;
    Section section = Section.INVENTORY;
    final IntLinkedOpenHashSet selection = new IntLinkedOpenHashSet();
    private int anchor = Integer.MIN_VALUE;
    private int modScroll;

    InventoryPanel(AdminScreen screen) {
        this.screen = screen;
    }

    void layout() {
        x = AdminScreen.SIDEBAR;
        y = AdminScreen.TOP + 26 + 66;
        bottom = screen.vh - AdminScreen.STATUS;
    }

    private List<Cell> cells(@Nullable Payloads.StateResponse state) {
        List<Cell> cells = new ArrayList<>();
        int left = x + 8;
        switch (section) {
            case INVENTORY -> {
                String[] hints = {"H", "C", "L", "B"};
                for (int i = 0; i < 4; i++) {
                    cells.add(new Cell(Slots.ARMOR_START + 3 - i, left + i * 18, y + 36, hints[i]));
                }
                cells.add(new Cell(Slots.OFFHAND, left + 4 * 18 + 10, y + 36, "O"));
                for (int slot = 9; slot < Slots.MAIN_SIZE; slot++) {
                    cells.add(new Cell(slot, left + (slot % 9) * 18, y + 72 + (slot / 9 - 1) * 18, null));
                }
                for (int slot = 0; slot < 9; slot++) {
                    cells.add(new Cell(slot, left + slot * 18, y + 130, null));
                }
            }
            case ENDER_CHEST -> {
                for (int i = 0; i < Slots.ENDER_SIZE; i++) {
                    cells.add(new Cell(Slots.ENDER_START + i, left + (i % 9) * 18, y + 36 + (i / 9) * 18, null));
                }
            }
            case MOD_SLOTS -> cells.addAll(modLayout(state, null));
        }
        return cells;
    }

    private IntList modSlots(@Nullable Payloads.StateResponse state) {
        IntLinkedOpenHashSet slots = new IntLinkedOpenHashSet();
        if (state != null) {
            state.items().keySet().forEach((int slot) -> {
                if (Slots.isExtra(slot)) {
                    slots.add(slot);
                }
            });
            Int2ObjectMap<ItemStack> base = screen.model.baseItems();
            if (base != null) {
                base.keySet().forEach((int slot) -> {
                    if (Slots.isExtra(slot)) {
                        slots.add(slot);
                    }
                });
            }
            // The slots the player has now, so empty ones can be seen and restored into.
            for (int slot : state.liveSlots()) {
                slots.add(slot);
            }
        }
        IntArrayList sorted = new IntArrayList(slots);
        sorted.sort(ModSlotNames.ORDER);
        return sorted;
    }

    /**
     * Places the mod slots in groups, one per mod with its name above, nine to a row, shifted by the scroll position.
     *
     * @param headers receives the group names, may be null
     */
    private List<Cell> modLayout(@Nullable Payloads.StateResponse state, @Nullable List<Header> headers) {
        List<Cell> cells = new ArrayList<>();
        int left = x + 8;
        int cy = modTop() + 2 - modScroll;
        String group = null;
        int column = 0;
        for (int slot : modSlots(state)) {
            String slotGroup = ModSlotNames.group(slot);
            if (!slotGroup.equals(group)) {
                if (group != null) {
                    cy += 18 + 4;
                }
                group = slotGroup;
                column = 0;
                if (headers != null) {
                    headers.add(new Header(ModSlotNames.groupName(slotGroup), cy));
                }
                cy += 11;
            } else if (column == 9) {
                column = 0;
                cy += 18;
            }
            cells.add(new Cell(slot, left + column * 18, cy, null));
            column++;
        }
        return cells;
    }

    private int modContentHeight(@Nullable Payloads.StateResponse state) {
        List<Cell> cells = modLayout(state, null);
        return cells.isEmpty() ? 0 : cells.get(cells.size() - 1).y() + 18 + modScroll - modTop() + 2;
    }

    private int maxModScroll(@Nullable Payloads.StateResponse state) {
        return Math.max(0, modContentHeight(state) - (modBottom() - modTop()));
    }

    boolean mouseScrolled(double mx, double my, double amount) {
        if (section != Section.MOD_SLOTS || mx < x || mx >= x + WIDTH || my < modTop() || my >= modBottom()) {
            return false;
        }
        modScroll = (int) Math.max(0, Math.min(maxModScroll(screen.model.current()), modScroll - amount * 18));
        return true;
    }

    /** Whether a cell is in the visible part of its tab (mod slots scroll). */
    private boolean visible(Cell cell) {
        return section != Section.MOD_SLOTS || cell.y() >= modTop() && cell.y() + 18 <= modBottom();
    }

    /**
     * The tabs that apply now. Long labels (other languages, many mod slots) switch to short ones, and are cut only when
     * even those do not fit.
     */
    private List<Tab> tabs(Font font, @Nullable Payloads.StateResponse state) {
        List<Section> shown = new ArrayList<>();
        int mods = modSlots(state).size();
        for (Section option : Section.values()) {
            if (option == Section.ENDER_CHEST && !screen.enderChest() || option == Section.MOD_SLOTS && mods == 0) {
                continue;
            }
            shown.add(option);
        }
        int available = WIDTH - 12;
        List<Component> labels = tabLabels(shown, mods, false);
        if (totalWidth(font, labels) > available) {
            labels = tabLabels(shown, mods, true);
        }
        int[] widths = new int[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            widths[i] = font.width(labels.get(i)) + 10;
        }
        widths = Theme.fit(widths, available);
        List<Tab> tabs = new ArrayList<>();
        int tx = x + 6;
        for (int i = 0; i < shown.size(); i++) {
            tabs.add(new Tab(shown.get(i), labels.get(i), tx, widths[i]));
            tx += widths[i];
        }
        return tabs;
    }

    private static List<Component> tabLabels(List<Section> sections, int mods, boolean compact) {
        List<Component> labels = new ArrayList<>();
        String suffix = compact ? "_short" : "";
        for (Section option : sections) {
            labels.add(switch (option) {
                case INVENTORY -> Component.translatable("inventorybackups.ui.inventory");
                case ENDER_CHEST -> Component.translatable("inventorybackups.ui.ender_chest" + suffix);
                case MOD_SLOTS -> Component.translatable("inventorybackups.ui.mod_slots" + suffix, mods);
            });
        }
        return labels;
    }

    private static int totalWidth(Font font, List<Component> labels) {
        int total = 0;
        for (Component label : labels) {
            total += font.width(label) + 10;
        }
        return total;
    }

    void clearSelectionIfSectionMissing() {
        if (section == Section.MOD_SLOTS && modSlots(screen.model.current()).isEmpty()) {
            section = Section.INVENTORY;
        }
    }

    void selectAll() {
        Payloads.StateResponse state = screen.model.current();
        selection.clear();
        for (Cell cell : cells(state)) {
            selection.add(cell.slot());
        }
    }

    int[] selectedSlots() {
        return selection.toIntArray();
    }

    void render(GuiGraphics g, int mx, int my) {
        Font font = screen.mc().font;
        Theme.vline(g, x + WIDTH - 1, y, bottom - y, Theme.LINE);
        Payloads.StateResponse state = screen.model.current();
        renderTabs(g, font, state, mx, my);
        if (section == Section.INVENTORY) {
            Theme.text(g, font, Component.translatable("inventorybackups.ui.equipment"), x + 8, y + 26, Theme.FAINT);
            Theme.text(g, font, Component.translatable("inventorybackups.ui.inventory"), x + 8, y + 60, Theme.FAINT);
        }
        if (state == null) {
            Theme.text(g, font, Component.translatable("inventorybackups.ui.loading"), x + 8, y + 160, Theme.FAINT);
        }
        Int2ObjectMap<ItemStack> base = ClientConfig.HIGHLIGHT_CHANGES.get() ? screen.model.baseItems() : null;
        Cell hovered = null;
        boolean mods = section == Section.MOD_SLOTS;
        if (mods) {
            modScroll = Math.min(modScroll, maxModScroll(state));
            g.enableScissor(sc(x), sc(modTop()), sc(x + WIDTH - 1), sc(modBottom()));
            List<Header> headers = new ArrayList<>();
            modLayout(state, headers);
            for (Header header : headers) {
                Theme.text(g, font, header.name(), x + 8, header.y() + 1, WIDTH - 20, Theme.FAINT);
            }
        }
        for (Cell cell : cells(state)) {
            if (mods && (cell.y() + 18 < modTop() || cell.y() > modBottom())) {
                continue;
            }
            ItemStack now = state == null ? ItemStack.EMPTY : state.items().getOrDefault(cell.slot(), ItemStack.EMPTY);
            ItemStack before = base == null ? null : base.getOrDefault(cell.slot(), ItemStack.EMPTY);
            boolean hover = visible(cell) && mx >= cell.x() && mx < cell.x() + 18 && my >= cell.y() && my < cell.y() + 18;
            renderCell(g, font, cell, now, before, hover);
            if (hover) {
                hovered = cell;
            }
        }
        if (mods) {
            g.disableScissor();
            renderScrollbar(g, state);
        }
        renderSelectedCard(g, font, state);
        renderKey(g, font);
        if (hovered != null && state != null) {
            tooltip(hovered, state, base);
        }
    }

    private void renderTabs(GuiGraphics g, Font font, @Nullable Payloads.StateResponse state, int mx, int my) {
        for (Tab tab : tabs(font, state)) {
            boolean on = section == tab.section();
            boolean hover = mx >= tab.x() && mx < tab.x() + tab.w() && my >= y + 5 && my < y + 18;
            Theme.text(g, font, tab.label(), tab.x() + 5, y + 8, tab.w() - 6, on || hover ? Theme.TEXT : Theme.MUTED);
            g.fill(tab.x(), y + 18, tab.x() + tab.w(), on ? y + 20 : y + 19, on ? Theme.ACCENT : Theme.LINE_2);
        }
    }

    private void renderScrollbar(GuiGraphics g, @Nullable Payloads.StateResponse state) {
        int max = maxModScroll(state);
        if (max <= 0) {
            return;
        }
        int track = modBottom() - modTop();
        int thumb = Math.max(10, track * track / (track + max));
        int ty = modTop() + (track - thumb) * modScroll / max;
        g.fill(x + WIDTH - 5, modTop(), x + WIDTH - 3, modBottom(), Theme.PANEL_2);
        g.fill(x + WIDTH - 5, ty, x + WIDTH - 3, ty + thumb, Theme.LINE_2);
    }

    private int sc(int virtual) {
        return Math.round(virtual * screen.scale);
    }

    private void renderCell(GuiGraphics g, Font font, Cell cell, ItemStack now, @Nullable ItemStack before, boolean hover) {
        int cx = cell.x();
        int cy = cell.y();
        Diff diff = diff(now, before);
        int fill = switch (diff) {
            case ADDED, REPLACED -> Theme.ADDED_BG;
            case REMOVED -> Theme.REMOVED_BG;
            default -> hover ? Theme.SLOT_HOVER : Theme.SLOT;
        };
        Theme.slot(g, cx, cy, fill);
        if (diff != Diff.SAME) {
            int border = switch (diff) {
                case REMOVED -> Theme.REMOVED;
                case COUNT -> Theme.CHANGED;
                default -> Theme.ADDED;
            };
            Theme.outline(g, cx, cy, 18, 18, border);
        }
        if (!now.isEmpty()) {
            g.renderItem(now, cx + 1, cy + 1);
            g.renderItemDecorations(font, now, cx + 1, cy + 1);
        } else if (diff == Diff.REMOVED && before != null) {
            g.renderItem(before, cx + 1, cy + 1);
            g.fill(cx + 1, cy + 1, cx + 17, cy + 17, 0xA03D1F22);
        } else if (cell.hint() != null) {
            Theme.text(g, font, cell.hint(), cx + 6, cy + 5, 0xFF3A404C);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (diff == Diff.COUNT && before != null) {
            int delta = now.getCount() - before.getCount();
            String badge = (delta > 0 ? "+" : "") + delta;
            int bw = font.width(badge) + 2;
            g.fill(cx - 2, cy - 4, cx - 2 + bw, cy + 4, delta > 0 ? Theme.ADDED : Theme.REMOVED);
            Theme.text(g, font, badge, cx - 1, cy - 4, 0xFF000000);
        }
        if (selection.contains(cell.slot())) {
            Theme.outline(g, cx - 1, cy - 1, 20, 20, Theme.ACCENT);
        }
        g.pose().popPose();
    }

    enum Diff {
        SAME, ADDED, REMOVED, COUNT, REPLACED
    }

    static Diff diff(ItemStack now, @Nullable ItemStack before) {
        if (before == null) {
            return Diff.SAME;
        }
        if (now.isEmpty() && before.isEmpty()) {
            return Diff.SAME;
        }
        if (before.isEmpty()) {
            return Diff.ADDED;
        }
        if (now.isEmpty()) {
            return Diff.REMOVED;
        }
        if (ItemStack.isSameItemSameTags(now, before)) {
            return now.getCount() == before.getCount() ? Diff.SAME : Diff.COUNT;
        }
        return Diff.REPLACED;
    }

    private void renderSelectedCard(GuiGraphics g, Font font, @Nullable Payloads.StateResponse state) {
        int cy = section == Section.ENDER_CHEST ? y + 36 + 18 * 3 + 8 : y + 156;
        int cx = x + 6;
        int cw = WIDTH - 12;
        Theme.box(g, cx, cy, cw, 46, Theme.PANEL, Theme.LINE);
        if (selection.isEmpty() || state == null) {
            int lines = Theme.wrapped(g, font, Component.translatable("inventorybackups.ui.select_hint"), cx + 4, cy + 4, cw - 8, 2,
                    Theme.FAINT);
            Theme.wrapped(g, font, Component.translatable("inventorybackups.ui.select_hint2"), cx + 4, cy + 4 + lines * 10, cw - 8,
                    4 - lines, Theme.FAINT);
            return;
        }
        int slot = selection.iterator().nextInt();
        ItemStack stack = state.items().getOrDefault(slot, ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            g.renderItem(stack, cx + 4, cy + 4);
            Theme.text(g, font, stack.getHoverName(), cx + 24, cy + 5, cw - 28, Theme.NAME);
        } else {
            Theme.text(g, font, Component.translatable("inventorybackups.ui.empty_slot"), cx + 24, cy + 5, cw - 28, Theme.MUTED);
        }
        Component where = slotName(slot);
        long since = state.since().getOrDefault(slot, -1L);
        Component sub = since > 0 ? Component.translatable("inventorybackups.ui.slot_since", where, Formats.time(since)) : where;
        Theme.text(g, font, sub, cx + 24, cy + 16, cw - 28, Theme.FAINT);
        Component count = Component.translatable("inventorybackups.ui.selected", selection.size());
        Theme.text(g, font, count, cx + 4, cy + 32, cw - 8, Theme.ACCENT);
    }

    /** The color key below the slots; on one line when it fits, otherwise on two. */
    private void renderKey(GuiGraphics g, Font font) {
        Object[][] keys = {{Theme.ADDED, "inventorybackups.ui.key.added"}, {Theme.REMOVED, "inventorybackups.ui.key.removed"},
                {Theme.CHANGED, "inventorybackups.ui.key.count"}};
        int available = WIDTH - 12;
        int total = 0;
        for (Object[] key : keys) {
            total += 8 + font.width(Component.translatable((String) key[1])) + 10;
        }
        boolean oneLine = total - 10 <= available;
        int ky = oneLine ? bottom - 12 : bottom - 22;
        int kx = x + 6;
        for (Object[] key : keys) {
            Component label = Component.translatable((String) key[1]);
            int w = 8 + font.width(label);
            if (kx + w > x + 6 + available) {
                kx = x + 6;
                ky += 10;
            }
            g.fill(kx, ky + 1, kx + 6, ky + 7, (int) key[0]);
            Theme.text(g, font, label, kx + 8, ky, available - (kx - x - 6) - 8, Theme.FAINT);
            kx += w + 10;
        }
    }

    private void tooltip(Cell cell, Payloads.StateResponse state, @Nullable Int2ObjectMap<ItemStack> base) {
        ItemStack now = state.items().getOrDefault(cell.slot(), ItemStack.EMPTY);
        ItemStack before = base == null ? ItemStack.EMPTY : base.getOrDefault(cell.slot(), ItemStack.EMPTY);
        ItemStack shown = now.isEmpty() ? before : now;
        List<Component> lines = new ArrayList<>();
        if (!shown.isEmpty()) {
            lines.addAll(Screen.getTooltipFromItem(screen.mc(), shown));
        } else {
            lines.add(slotName(cell.slot()));
        }
        if (ClientConfig.SLOT_HISTORY.get()) {
            lines.add(Component.literal(""));
            lines.add(slotName(cell.slot()).copy().withStyle(style -> style.withColor(Theme.FAINT)));
            if (now.isEmpty() && !before.isEmpty()) {
                lines.add(Component.translatable("inventorybackups.ui.tooltip.removed", Formats.time(state.entry().time()))
                        .withStyle(style -> style.withColor(Theme.REMOVED)));
            } else if (!now.isEmpty()) {
                long since = state.since().getOrDefault(cell.slot(), -1L);
                if (since > 0) {
                    lines.add(Component.translatable("inventorybackups.ui.tooltip.since", Formats.time(since))
                            .withStyle(style -> style.withColor(Theme.ADDED)));
                    EntryRow origin = screen.model.entryAt(since);
                    if (origin != null && !origin.dimension().isEmpty()) {
                        lines.add(Component.translatable("inventorybackups.ui.tooltip.where", Formats.place(origin.dimension(),
                                origin.pos().getX(), origin.pos().getY(), origin.pos().getZ()))
                                        .withStyle(style -> style.withColor(Theme.MUTED)));
                    }
                    if (origin != null && !origin.detail().isEmpty()) {
                        lines.add(Component.literal(origin.detail()).withStyle(style -> style.withColor(Theme.MUTED)));
                    }
                } else if (since == 0) {
                    lines.add(Component.translatable("inventorybackups.ui.tooltip.long_ago")
                            .withStyle(style -> style.withColor(Theme.MUTED)));
                }
                if (!before.isEmpty() && ItemStack.isSameItemSameTags(now, before) && before.getCount() != now.getCount()) {
                    lines.add(Component.translatable("inventorybackups.ui.tooltip.count", before.getCount(), now.getCount())
                            .withStyle(style -> style.withColor(Theme.CHANGED)));
                }
            }
            lines.add(Component.translatable("inventorybackups.ui.tooltip.click").withStyle(style -> style.withColor(Theme.FAINT)));
        }
        screen.showTooltip(lines, shown);
    }

    static Component slotName(int slot) {
        return switch (Slots.section(slot)) {
            case MAIN -> slot < 9 ? Component.translatable("inventorybackups.ui.slot.hotbar", slot + 1)
                    : Component.translatable("inventorybackups.ui.slot.main", slot - 8);
            case ARMOR -> Component.translatable("inventorybackups.ui.slot.armor." + (slot - Slots.ARMOR_START));
            case OFFHAND -> Component.translatable("inventorybackups.ui.slot.offhand");
            case CURIOS, MOD -> ModSlotNames.name(slot);
            case ENDER_CHEST -> Component.translatable("inventorybackups.ui.slot.ender", slot - Slots.ENDER_START + 1);
            case UNKNOWN -> Component.literal(Integer.toString(slot));
        };
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (mx < x || mx >= x + WIDTH || my < y || my >= bottom) {
            return false;
        }
        Font font = screen.mc().font;
        Payloads.StateResponse state = screen.model.current();
        if (my >= y + 5 && my < y + 20) {
            for (Tab tab : tabs(font, state)) {
                if (mx >= tab.x() && mx < tab.x() + tab.w()) {
                    section = tab.section();
                    selection.clear();
                    modScroll = 0;
                    return true;
                }
            }
            return true;
        }
        List<Cell> cells = cells(state);
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            if (visible(cell) && mx >= cell.x() && mx < cell.x() + 18 && my >= cell.y() && my < cell.y() + 18) {
                if (button == 1) {
                    selection.clear();
                } else if (Screen.hasShiftDown() && anchor != Integer.MIN_VALUE) {
                    selectRange(cells, anchor, cell.slot());
                } else if (!selection.remove(cell.slot())) {
                    selection.add(cell.slot());
                    anchor = cell.slot();
                }
                return true;
            }
        }
        return true;
    }

    private void selectRange(List<Cell> cells, int from, int to) {
        int a = -1;
        int b = -1;
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).slot() == from) {
                a = i;
            }
            if (cells.get(i).slot() == to) {
                b = i;
            }
        }
        if (a < 0 || b < 0) {
            return;
        }
        for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
            selection.add(cells.get(i).slot());
        }
    }
}
