package com.pockyl.inventorybackups.client.screen;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import com.pockyl.inventorybackups.backup.Slots;
import com.pockyl.inventorybackups.client.Formats;
import com.pockyl.inventorybackups.network.EntryRow;
import com.pockyl.inventorybackups.network.Payloads;
import com.pockyl.inventorybackups.storage.Trigger;

import java.util.ArrayList;
import java.util.List;

/**
 * What changed (against the previous entry or a pin), the details of the selected entry, and the admin actions, each
 * behind a confirmation.
 */
final class DetailsPanel {
    private static final int ROW = 18;
    private static final int LINE = 11;

    private final AdminScreen screen;
    private int x;
    private int y;
    private int w;
    private int bottom;
    private int changesScroll;
    private final List<UiButton> actions = new ArrayList<>();
    private final List<UiButton> links = new ArrayList<>();

    DetailsPanel(AdminScreen screen) {
        this.screen = screen;
    }

    void layout() {
        x = AdminScreen.SIDEBAR + InventoryPanel.WIDTH;
        y = AdminScreen.TOP + 26 + 66;
        w = screen.vw - x;
        bottom = screen.vh - AdminScreen.STATUS;
        actions.clear();
        int aw = (w - 12 - 9) / 4;
        int ay = bottom - 22;
        actions.add(new UiButton(x + 6, ay, aw, 16, Component.translatable("inventorybackups.ui.action.restore_all"),
                UiButton.Style.PRIMARY,
                this::restoreAll));
        actions.add(new UiButton(x + 6 + (aw + 3), ay, aw, 16, Component.translatable("inventorybackups.ui.action.restore_selected", 0),
                UiButton.Style.NORMAL, this::restoreSelected));
        actions.add(new UiButton(x + 6 + 2 * (aw + 3), ay, aw, 16, Component.translatable("inventorybackups.ui.action.copy"),
                UiButton.Style.NORMAL, this::giveCopy));
        actions.add(new UiButton(x + 6 + 3 * (aw + 3), ay, aw, 16, Component.translatable("inventorybackups.ui.action.live"),
                UiButton.Style.NORMAL, this::liveEdit));
        links.clear();
    }

    void cycleBase() {
        TimelineModel model = screen.model;
        TimelineModel.Base next = switch (model.base) {
            case PREVIOUS -> model.pinA != null ? TimelineModel.Base.A : model.pinB != null ? TimelineModel.Base.B
                    : TimelineModel.Base.PREVIOUS;
            case A -> model.pinB != null ? TimelineModel.Base.B : TimelineModel.Base.PREVIOUS;
            case B -> TimelineModel.Base.PREVIOUS;
        };
        model.base = next;
        screen.requestState();
    }

    private int entryTop() {
        return bottom - 28 - (LINE * 7 + 18);
    }

    void render(GuiGraphics g, int mx, int my) {
        Font font = screen.mc().font;
        Payloads.StateResponse state = screen.model.current();
        int sx = x + 6;
        int sw = w - 12;

        Theme.text(g, font, Component.translatable("inventorybackups.ui.changed"), sx, y + 6, Theme.MUTED);
        renderBaseToggle(g, font, sx + sw, y + 6, mx, my);
        Theme.hline(g, sx, y + 17, sw, Theme.LINE);
        int listTop = y + 18;
        int listBottom = entryTop() - 6;
        List<Change> changes = changes(state);
        g.enableScissor(sc(sx), sc(listTop), sc(sx + sw), sc(listBottom));
        int ry = listTop - changesScroll;
        Change hovered = null;
        for (Change change : changes) {
            if (ry + ROW > listTop && ry < listBottom) {
                renderChange(g, font, change, sx, ry, sw);
                if (mx >= sx && mx < sx + sw && my >= Math.max(ry, listTop) && my < Math.min(ry + ROW, listBottom)) {
                    hovered = change;
                }
            }
            ry += ROW;
        }
        if (state != null && changes.isEmpty()) {
            Theme.text(g, font, Component.translatable(screen.model.baseItems() == null ? "inventorybackups.ui.loading"
                    : "inventorybackups.ui.nothing_changed"), sx + 2, listTop + 5, sw - 4, Theme.FAINT);
        }
        g.disableScissor();
        if (hovered != null) {
            ItemStack stack = hovered.now().isEmpty() ? hovered.before() : hovered.now();
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(screen.mc(), stack));
            screen.showTooltip(lines, stack);
        }

        renderEntry(g, font, sx, entryTop(), sw, mx, my);

        actions.get(1).label = Component.translatable("inventorybackups.ui.action.restore_selected", screen.inventoryPanel()
                .selection.size());
        boolean online = screen.model.player != null && screen.model.player.online();
        boolean hasState = state != null;
        actions.get(0).enabled = screen.canRestore() && online && hasState;
        actions.get(1).enabled = screen.canRestore() && online && hasState && !screen.inventoryPanel().selection.isEmpty();
        actions.get(2).enabled = screen.canRestore() && hasState && !screen.inventoryPanel().selection.isEmpty();
        actions.get(3).enabled = screen.canEdit() && online;
        for (UiButton action : actions) {
            action.render(g, font, mx, my);
            if (!action.enabled && action.contains(mx, my)) {
                screen.showTooltip(List.of(Component.translatable(online || action == actions.get(2)
                        ? "inventorybackups.ui.action.unavailable" : "inventorybackups.ui.offline")), ItemStack.EMPTY);
            }
        }
    }

    private void renderBaseToggle(GuiGraphics g, Font font, int right, int ty, int mx, int my) {
        TimelineModel model = screen.model;
        Component[] labels = {Component.translatable("inventorybackups.ui.base.previous"), Component.literal("A"), Component.literal("B")};
        TimelineModel.Base[] bases = TimelineModel.Base.values();
        int tx = right;
        for (int i = labels.length - 1; i >= 0; i--) {
            boolean available = i == 0 || (i == 1 ? model.pinA != null : model.pinB != null);
            int lw = font.width(labels[i]);
            tx -= lw;
            int color = model.base == bases[i] ? Theme.ACCENT : available ? Theme.MUTED : Theme.FAINT;
            Theme.text(g, font, labels[i], tx, ty, color);
            if (i > 0) {
                tx -= font.width(" · ");
                Theme.text(g, font, " · ", tx, ty, Theme.FAINT);
            }
        }
        Component vs = Component.translatable("inventorybackups.ui.base.vs");
        tx -= font.width(vs) + 3;
        Theme.text(g, font, vs, tx, ty, Theme.FAINT);
    }

    /** One changed slot: what it holds now and what it held in the compared state. */
    private record Change(int slot, ItemStack now, ItemStack before) {
    }

    private List<Change> changes(Payloads.StateResponse state) {
        List<Change> changes = new ArrayList<>();
        Int2ObjectMap<ItemStack> base = screen.model.baseItems();
        if (state == null || base == null) {
            return changes;
        }
        IntOpenHashSet slots = new IntOpenHashSet(state.items().keySet());
        slots.addAll(base.keySet());
        IntArrayList sorted = new IntArrayList(slots);
        sorted.sort((a, b) -> Integer.compare(order(a), order(b)));
        for (int slot : sorted) {
            ItemStack now = state.items().getOrDefault(slot, ItemStack.EMPTY);
            ItemStack before = base.getOrDefault(slot, ItemStack.EMPTY);
            if (InventoryPanel.diff(now, before) != InventoryPanel.Diff.SAME) {
                changes.add(new Change(slot, now, before));
            }
        }
        return changes;
    }

    /** Sort order: hotbar and main inventory, armor, offhand, mod slots, ender chest. */
    private static int order(int slot) {
        return switch (Slots.section(slot)) {
            case MAIN -> slot;
            case ARMOR -> 100 + slot;
            case OFFHAND -> 300;
            case CURIOS -> 1000 + slot;
            case MOD -> 2_000_000 + slot;
            case ENDER_CHEST -> 3_000_000 + slot;
            case UNKNOWN -> 4_000_000 + slot;
        };
    }

    private void renderChange(GuiGraphics g, Font font, Change change, int rx, int ry, int rw) {
        Theme.hline(g, rx, ry + ROW - 1, rw, Theme.ROW_LINE);
        ItemStack shown = change.now().isEmpty() ? change.before() : change.now();
        g.renderItem(shown, rx + 1, ry + 1);
        MutableComponent text;
        InventoryPanel.Diff diff = InventoryPanel.diff(change.now(), change.before());
        switch (diff) {
            case COUNT -> {
                int delta = change.now().getCount() - change.before().getCount();
                int deltaColor = delta > 0 ? Theme.ADDED : Theme.REMOVED;
                text = Component.literal((delta > 0 ? "+" : "") + delta + " ").withStyle(style -> style.withColor(deltaColor))
                        .append(shown.getHoverName().copy().withStyle(style -> style.withColor(Theme.TEXT)))
                        .append(Component.literal(" (" + change.before().getCount() + " → " + change.now().getCount() + ")")
                                .withStyle(style -> style.withColor(Theme.FAINT)));
            }
            case REMOVED -> text = Component.literal("-" + change.before().getCount() + " ")
                    .withStyle(style -> style.withColor(Theme.REMOVED))
                    .append(change.before().getHoverName().copy().withStyle(style -> style.withColor(Theme.TEXT)));
            case REPLACED -> text = Component.literal("+" + change.now().getCount() + " ").withStyle(style -> style.withColor(Theme.ADDED))
                    .append(change.now().getHoverName().copy().withStyle(style -> style.withColor(Theme.TEXT)))
                    .append(Component.translatable("inventorybackups.ui.replaced", change.before().getHoverName())
                            .withStyle(style -> style.withColor(Theme.FAINT)));
            default -> text = Component.literal("+" + change.now().getCount() + " ").withStyle(style -> style.withColor(Theme.ADDED))
                    .append(change.now().getHoverName().copy().withStyle(style -> style.withColor(Theme.TEXT)));
        }
        Component where = InventoryPanel.slotName(change.slot());
        int whereWidth = font.width(where);
        Theme.text(g, font, text, rx + 20, ry + 5, rw - 26 - whereWidth, Theme.TEXT);
        Theme.textRight(g, font, where, rx + rw, ry + 5, Theme.FAINT);
    }

    private void renderEntry(GuiGraphics g, Font font, int ex, int ey, int ew, int mx, int my) {
        EntryRow row = screen.model.selected();
        Theme.text(g, font, Component.translatable("inventorybackups.ui.entry"), ex, ey + 2, Theme.MUTED);
        Theme.hline(g, ex, ey + 13, ew, Theme.LINE);
        links.clear();
        int right = ex + ew;
        if (screen.canView() && screen.model.player != null && screen.model.player.online()) {
            right = link(g, font, right, ey + 2, Component.translatable("inventorybackups.ui.action.snapshot"), mx, my,
                    () -> screen.sendAction(Payloads.Action.SNAPSHOT, new int[0]));
        }
        if (screen.canRestore() && screen.model.player != null && screen.model.player.online()) {
            link(g, font, right, ey + 2, Component.translatable("inventorybackups.ui.action.undo"), mx, my, this::undo);
        }
        if (row == null) {
            return;
        }
        int ly = ey + 18;
        int labelWidth = 52;
        ly = contextLine(g, font, ex, ly, labelWidth, ew, Component.translatable("inventorybackups.ui.ctx.trigger"),
                Component.literal("■ ").append(Component.translatable("inventorybackups.trigger." + row.trigger().key()))
                        .withStyle(style -> style.withColor(Theme.color(row.trigger()))));
        ly = contextLine(g, font, ex, ly, labelWidth, ew, Component.translatable("inventorybackups.ui.ctx.time"),
                Component.literal(Formats.full(row.time()) + " ").append(Component.literal("(").append(Formats.ago(row.time())).append(")")
                        .withStyle(style -> style.withColor(Theme.FAINT))));
        if (!row.dimension().isEmpty()) {
            Component place = Component.literal(Formats.place(row.dimension(), row.pos().getX(), row.pos().getY(), row.pos().getZ()));
            contextLine(g, font, ex, ly, labelWidth, ew, Component.translatable("inventorybackups.ui.ctx.where"), place);
            if (screen.canEdit()) {
                int px = ex + labelWidth + font.width(place) + 6;
                link(g, font, Math.min(px + font.width(Component.translatable("inventorybackups.ui.action.teleport")), right), ly,
                        Component.translatable("inventorybackups.ui.action.teleport"), mx, my,
                        () -> screen.sendAction(Payloads.Action.TELEPORT, new int[0]));
            }
            ly += LINE;
        }
        if (!row.detail().isEmpty()) {
            Component label = Component.translatable(switch (row.trigger()) {
                case DEATH -> "inventorybackups.ui.ctx.cause";
                case CONTAINER -> "inventorybackups.ui.ctx.container";
                case RESTORE -> "inventorybackups.ui.ctx.restored";
                default -> "inventorybackups.ui.ctx.note";
            });
            Component value = Component.literal(row.detail());
            if (row.trigger() == Trigger.RESTORE) {
                try {
                    value = Component.literal(Formats.full(Long.parseLong(row.detail())));
                } catch (NumberFormatException e) {
                    value = Component.literal(row.detail());
                }
            } else if (row.trigger() == Trigger.CONTAINER && row.containerPos() != null) {
                value = Component.literal(row.detail() + " " + row.containerPos().getX() + " " + row.containerPos().getY() + " "
                        + row.containerPos().getZ());
            }
            ly = contextLine(g, font, ex, ly, labelWidth, ew, label, value);
        }
        if (!row.actor().isEmpty()) {
            ly = contextLine(g, font, ex, ly, labelWidth, ew, Component.translatable("inventorybackups.ui.ctx.admin"),
                    Component.literal(row.actor()));
        }
        contextLine(g, font, ex, ly, labelWidth, ew, Component.translatable("inventorybackups.ui.ctx.changed"),
                Component.translatable("inventorybackups.entry.changed", row.changed()));
    }

    private int contextLine(GuiGraphics g, Font font, int lx, int ly, int labelWidth, int width, Component label, Component value) {
        Theme.text(g, font, label, lx, ly, Theme.FAINT);
        Theme.text(g, font, value, lx + labelWidth, ly, width - labelWidth, Theme.TEXT);
        return ly + LINE;
    }

    /** A text link drawn right-aligned at {@code right}; returns the x where the next link may end. */
    private int link(GuiGraphics g, Font font, int right, int ly, Component label, int mx, int my, Runnable action) {
        int lw = font.width(label);
        int lx = right - lw;
        boolean hover = mx >= lx && mx < right && my >= ly - 1 && my < ly + 9;
        Theme.text(g, font, label, lx, ly, hover ? Theme.TEXT : Theme.ACCENT);
        links.add(new UiButton(lx, ly - 1, lw, 10, label, UiButton.Style.GHOST, action));
        return lx - 8;
    }

    private int sc(int virtual) {
        return Math.round(virtual * screen.scale);
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (mx < x || my < y || my >= bottom) {
            return false;
        }
        for (UiButton action : actions) {
            if (action.click(mx, my)) {
                return true;
            }
        }
        for (UiButton link : links) {
            if (link.click(mx, my)) {
                return true;
            }
        }
        if (my >= y + 4 && my < y + 16 && mx > x + w / 2) {
            cycleBase();
            return true;
        }
        return true;
    }

    boolean mouseScrolled(double mx, double my, double amount) {
        if (mx < x || my < y || my >= entryTop()) {
            return false;
        }
        int max = Math.max(0, changes(screen.model.current()).size() * ROW - (entryTop() - 6 - y - 18));
        changesScroll = (int) Math.max(0, Math.min(max, changesScroll - amount * ROW));
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------------------------------------

    private String who() {
        return screen.model.player == null ? "" : screen.model.player.name();
    }

    private String when() {
        EntryRow row = screen.model.selected();
        return row == null ? "" : Formats.full(row.time());
    }

    private void restoreAll() {
        List<ConfirmDialog.Choice> choices = new ArrayList<>();
        choices.add(new ConfirmDialog.Choice(Component.translatable("inventorybackups.ui.cancel"), UiButton.Style.GHOST, () -> {
        }));
        choices.add(new ConfirmDialog.Choice(Component.translatable("inventorybackups.ui.inventory"), UiButton.Style.NORMAL,
                () -> screen.sendAction(Payloads.Action.RESTORE_INVENTORY, new int[0])));
        if (screen.enderChest()) {
            choices.add(new ConfirmDialog.Choice(Component.translatable("inventorybackups.ui.ender_chest"), UiButton.Style.NORMAL,
                    () -> screen.sendAction(Payloads.Action.RESTORE_ENDER_CHEST, new int[0])));
        }
        choices.add(new ConfirmDialog.Choice(Component.translatable("inventorybackups.ui.both"), UiButton.Style.PRIMARY,
                () -> screen.sendAction(Payloads.Action.RESTORE_ALL, new int[0])));
        screen.confirmAction(Component.translatable("inventorybackups.ui.confirm.restore_all.title"),
                List.of(Component.translatable("inventorybackups.ui.confirm.restore_all", who(), when()),
                        Component.translatable("inventorybackups.ui.confirm.recorded", screen.mc().getUser().getName())), choices);
    }

    private void restoreSelected() {
        int[] slots = screen.inventoryPanel().selectedSlots();
        screen.confirmAction(Component.translatable("inventorybackups.ui.confirm.restore_selected.title", slots.length),
                List.of(Component.translatable("inventorybackups.ui.confirm.restore_selected", slots.length, who(), when()),
                        Component.translatable("inventorybackups.ui.confirm.recorded", screen.mc().getUser().getName())),
                List.of(cancel(), new ConfirmDialog.Choice(Component.translatable("inventorybackups.ui.restore"), UiButton.Style.PRIMARY,
                        () -> screen.sendAction(Payloads.Action.RESTORE_SLOTS, slots))));
    }

    private void giveCopy() {
        int[] slots = screen.inventoryPanel().selectedSlots();
        screen.confirmAction(Component.translatable("inventorybackups.ui.confirm.copy.title"),
                List.of(Component.translatable("inventorybackups.ui.confirm.copy", slots.length, who(), when())),
                List.of(cancel(), new ConfirmDialog.Choice(Component.translatable("inventorybackups.ui.action.copy"),
                        UiButton.Style.PRIMARY,
                        () -> screen.sendAction(Payloads.Action.GIVE_COPY, slots))));
    }

    private void liveEdit() {
        boolean ender = screen.inventoryPanel().section == InventoryPanel.Section.ENDER_CHEST;
        screen.confirmAction(Component.translatable("inventorybackups.ui.confirm.live.title"),
                List.of(Component.translatable(ender ? "inventorybackups.ui.confirm.live_ender" : "inventorybackups.ui.confirm.live",
                        who())),
                List.of(cancel(), new ConfirmDialog.Choice(Component.translatable("inventorybackups.ui.action.live"),
                        UiButton.Style.PRIMARY,
                        () -> screen.sendAction(ender ? Payloads.Action.LIVE_ENDER_CHEST : Payloads.Action.LIVE_INVENTORY, new int[0]))));
    }

    private void undo() {
        screen.confirmAction(Component.translatable("inventorybackups.ui.confirm.undo.title"),
                List.of(Component.translatable("inventorybackups.ui.confirm.undo", who())),
                List.of(cancel(), new ConfirmDialog.Choice(Component.translatable("inventorybackups.ui.action.undo"), UiButton.Style.DANGER,
                        () -> screen.sendAction(Payloads.Action.UNDO, new int[0]))));
    }

    private static ConfirmDialog.Choice cancel() {
        return new ConfirmDialog.Choice(Component.translatable("inventorybackups.ui.cancel"), UiButton.Style.GHOST, () -> {
        });
    }
}
