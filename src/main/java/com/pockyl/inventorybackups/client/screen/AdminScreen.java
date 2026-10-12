package com.pockyl.inventorybackups.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.pockyl.inventorybackups.client.ClientConfig;
import com.pockyl.inventorybackups.client.ClientSession;
import com.pockyl.inventorybackups.client.Formats;
import com.pockyl.inventorybackups.client.KeyBindings;
import com.pockyl.inventorybackups.network.EntryRow;
import com.pockyl.inventorybackups.network.Payloads;
import com.pockyl.inventorybackups.network.PlayerRow;
import com.pockyl.inventorybackups.permission.Permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The admin screen: a player browser and a timeline with the inventory at the selected moment, and an item search.
 * <p>
 * It is laid out for at least 640 x 360 GUI pixels. On smaller GUI sizes everything is drawn scaled down by a factor that
 * keeps whole real pixels per GUI pixel, so the font stays crisp; on larger ones the layout stretches. Panels work in
 * these virtual coordinates; only tooltips are drawn unscaled, at the real mouse position.
 */
public final class AdminScreen extends Screen {
    static final int MIN_WIDTH = 640;
    static final int MIN_HEIGHT = 360;
    static final int TOP = 22;
    static final int STATUS = 14;
    static final int SIDEBAR = 156;
    private static final long TOAST_MILLIS = 4000;

    enum Tab {
        TIMELINE, SEARCH
    }

    private Payloads.Hello hello;
    final TimelineModel model = new TimelineModel();
    Tab tab = Tab.TIMELINE;
    float scale = 1;
    int vw;
    int vh;
    private final PlayerListPanel players = new PlayerListPanel(this);
    private final TimelinePanel timeline = new TimelinePanel(this);
    private final InventoryPanel inventory = new InventoryPanel(this);
    private final DetailsPanel details = new DetailsPanel(this);
    private final SearchPanel search = new SearchPanel(this);
    @Nullable
    private ConfirmDialog dialog;
    private final List<Toast> toasts = new ArrayList<>();
    @Nullable
    private TooltipRequest tooltip;
    private int timelineRequest;
    private boolean stateInFlight;
    private long pendingState = Long.MIN_VALUE;
    private long refreshAt;
    private boolean playing;
    private long nextPlayStep;
    /** Whether the first timeline of the selected player may still be zoomed to fit a short history. */
    private boolean fitPending;

    private record Toast(Component message, boolean ok, long until) {
    }

    /** A tooltip a panel wants drawn this frame; drawn last and unscaled. */
    record TooltipRequest(List<Component> lines, ItemStack stack) {
    }

    public AdminScreen(Payloads.Hello hello) {
        super(Component.translatable("inventorybackups.ui.title"));
        this.hello = hello;
    }

    public void updatePermissions(Payloads.Hello newHello) {
        this.hello = newHello;
    }

    boolean can(int permission) {
        return hello.can(permission);
    }

    boolean canView() {
        return can(Permissions.ALL.indexOf(Permissions.VIEW));
    }

    boolean canRestore() {
        return can(Permissions.ALL.indexOf(Permissions.RESTORE));
    }

    boolean canEdit() {
        return can(Permissions.ALL.indexOf(Permissions.EDIT));
    }

    boolean canSearch() {
        return can(Permissions.ALL.indexOf(Permissions.SEARCH));
    }

    boolean enderChest() {
        return hello.enderChest();
    }

    Minecraft mc() {
        return minecraft;
    }

    @Override
    protected void init() {
        Window window = minecraft.getWindow();
        double real = Math.min(window.getWidth() / (double) MIN_WIDTH, window.getHeight() / (double) MIN_HEIGHT);
        double guiScale = window.getGuiScale();
        // Whole real pixels per virtual pixel keep the font crisp; never scale up beyond the GUI scale the admin chose.
        double pixels = Math.max(1, Math.floor(real));
        scale = (float) Math.min(1.0, pixels / guiScale);
        vw = Math.max(MIN_WIDTH, Math.round(width / scale));
        vh = Math.max(MIN_HEIGHT, Math.round(height / scale));
        players.layout();
        timeline.layout();
        inventory.layout();
        details.layout();
        search.layout();
        if (model.player == null) {
            loadPlayers("");
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Data
    // ---------------------------------------------------------------------------------------------------------------

    void loadPlayers(String filter) {
        ClientSession.send(id -> new Payloads.PlayersRequest(id, filter), payload -> {
            if (payload instanceof Payloads.PlayersResponse response) {
                players.setPlayers(response.players());
                if (model.player == null && !response.players().isEmpty()) {
                    UUID remembered = ClientConfig.REMEMBER_PLAYER.get() ? ClientSession.lastPlayer() : null;
                    PlayerRow pick = response.players().get(0);
                    for (PlayerRow row : response.players()) {
                        if (row.uuid().equals(remembered)) {
                            pick = row;
                        }
                    }
                    selectPlayer(pick, Long.MIN_VALUE);
                }
            } else {
                handleNotice(payload);
            }
        });
    }

    /**
     * Shows a player's timeline.
     *
     * @param at time to select, {@link Long#MIN_VALUE} for the newest entry
     */
    void selectPlayer(PlayerRow row, long at) {
        model.reset(row);
        ClientSession.rememberPlayer(row.uuid());
        long anchor = at != Long.MIN_VALUE ? at : row.online() ? System.currentTimeMillis() : Math.max(row.lastEntry(), row.lastSeen());
        model.centerOn(anchor > 0 ? anchor : System.currentTimeMillis());
        playing = false;
        fitPending = at == Long.MIN_VALUE;
        loadTimeline(at);
    }

    /** Loads the entries of the window; then selects the entry at {@code select}, or keeps the selection. */
    void loadTimeline(long select) {
        if (model.player == null) {
            return;
        }
        UUID player = model.player.uuid();
        model.loading = true;
        timelineRequest = ClientSession.send(id -> new Payloads.TimelineRequest(id, player, model.from, model.to), payload -> {
            if (payload instanceof Payloads.TimelineResponse response) {
                if (response.id() != timelineRequest || model.player == null || !response.player().equals(model.player.uuid())) {
                    return;
                }
                model.loading = false;
                boolean all = model.span <= 0;
                model.setEntries(response);
                if (all && response.first() > 0 && response.first() < model.from) {
                    model.centerOn(response.last());
                    loadTimeline(select);
                    return;
                }
                if (fitPending) {
                    fitPending = false;
                    if (model.fit()) {
                        loadTimeline(select);
                        return;
                    }
                }
                if (select != Long.MIN_VALUE) {
                    int index = model.indexAtOrBefore(select);
                    model.index = index >= 0 ? index : model.nearest(select);
                }
                requestState();
            } else {
                model.loading = false;
                handleNotice(payload);
            }
        });
    }

    /** Loads the selected state (and the pinned one compared with); only one state request is in flight at a time. */
    void requestState() {
        EntryRow row = model.selected();
        if (row != null && !model.states.containsKey(row.time())) {
            fetchState(row.time());
        }
        Long base = model.baseTime();
        if (base != null && !model.states.containsKey(base)) {
            fetchState(base);
        }
    }

    private void fetchState(long time) {
        if (model.player == null) {
            return;
        }
        if (stateInFlight) {
            pendingState = time;
            return;
        }
        stateInFlight = true;
        UUID player = model.player.uuid();
        ClientSession.send(id -> new Payloads.StateRequest(id, player, time), payload -> {
            stateInFlight = false;
            if (payload instanceof Payloads.StateResponse response && model.player != null && response.player()
                    .equals(model.player.uuid())) {
                model.states.put(response.entry().time(), response);
                // A request for an exact entry time answers for that time even if the entry list moved on.
                model.states.putIfAbsent(time, response);
            } else {
                handleNotice(payload);
            }
            long next = pendingState;
            pendingState = Long.MIN_VALUE;
            if (next != Long.MIN_VALUE && !model.states.containsKey(next)) {
                fetchState(next);
            } else {
                requestState();
            }
        });
    }

    void select(int index) {
        if (index < 0 || index >= model.entries.size() || index == model.index) {
            return;
        }
        model.index = index;
        inventory.clearSelectionIfSectionMissing();
        requestState();
    }

    void step(int delta) {
        if (model.entries.isEmpty()) {
            return;
        }
        select(Math.max(0, Math.min(model.entries.size() - 1, model.index + delta)));
    }

    void togglePlay() {
        playing = !playing;
        if (playing && model.index >= model.entries.size() - 1) {
            select(0);
        }
        nextPlayStep = Util.getMillis();
    }

    boolean playing() {
        return playing;
    }

    void zoomTo(ClientConfig.Zoom zoom) {
        model.span = TimelineModel.spanOf(zoom);
        long anchor = model.selected() != null ? model.selectedTime() : System.currentTimeMillis();
        model.centerOn(anchor);
        loadTimeline(model.selectedTime());
    }

    /** Asks for confirmation, then sends an admin action for the selected entry. */
    void confirmAction(Component title, List<Component> body, List<ConfirmDialog.Choice> choices) {
        dialog = new ConfirmDialog(this, title, body, choices);
    }

    void sendAction(Payloads.Action action, int[] slots) {
        EntryRow row = model.selected();
        if (model.player == null || row == null && action != Payloads.Action.SNAPSHOT && action != Payloads.Action.UNDO
                && action != Payloads.Action.LIVE_INVENTORY && action != Payloads.Action.LIVE_ENDER_CHEST) {
            return;
        }
        UUID player = model.player.uuid();
        long time = row == null ? Long.MAX_VALUE : row.time();
        ClientSession.send(id -> new Payloads.ActionRequest(id, player, time, action, slots), payload -> {
            if (payload instanceof Payloads.Notice notice) {
                toast(notice.message(), notice.ok());
                if (notice.ok()) {
                    // The restore, snapshot or copy shows up as a new entry once the server has written it.
                    refreshAt = Util.getMillis() + 1500;
                }
            }
        });
    }

    void runSearch(String query, @Nullable UUID player, long since, long at) {
        ClientSession.send(id -> new Payloads.SearchRequest(id, query, player, since, at), payload -> {
            if (payload instanceof Payloads.SearchResponse response) {
                search.setResult(response);
            } else {
                handleNotice(payload);
                search.searchFailed();
            }
        });
    }

    /** Opens a player's timeline at a time, from the search tab. */
    void openTimeline(UUID player, String name, long time) {
        PlayerRow row = players.find(player);
        if (row == null) {
            row = new PlayerRow(player, name, false, time, 0, time);
        }
        tab = Tab.TIMELINE;
        selectPlayer(row, time);
    }

    private void handleNotice(CustomPacketPayload payload) {
        if (payload instanceof Payloads.Notice notice) {
            toast(notice.message(), notice.ok());
        }
    }

    public void toast(Component message, boolean ok) {
        toasts.add(new Toast(message, ok, Util.getMillis() + TOAST_MILLIS));
        if (toasts.size() > 3) {
            toasts.remove(0);
        }
    }

    void showTooltip(List<Component> lines, ItemStack stack) {
        tooltip = new TooltipRequest(lines, stack);
    }

    @Override
    public void tick() {
        long now = Util.getMillis();
        if (playing && now >= nextPlayStep) {
            nextPlayStep = now + 1000L / ClientConfig.PLAY_SPEED.get();
            if (model.index >= model.entries.size() - 1) {
                playing = false;
            } else {
                step(1);
            }
        }
        if (refreshAt != 0 && now >= refreshAt) {
            refreshAt = 0;
            model.states.clear();
            long now2 = System.currentTimeMillis();
            if (model.to < now2) {
                model.to = now2 + TimelineModel.MINUTE;
                model.from = model.span > 0 ? model.to - model.span : model.from;
            }
            loadTimeline(Long.MAX_VALUE);
        }
        toasts.removeIf(toast -> toast.until() < now);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        tooltip = null;
        int mx = virtual(mouseX);
        int my = virtual(mouseY);
        boolean blocked = dialog != null;
        int pmx = blocked ? -1 : mx;
        int pmy = blocked ? -1 : my;
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        g.fill(0, 0, vw, vh, Theme.BG);
        renderTopBar(g, pmx, pmy);
        if (tab == Tab.TIMELINE) {
            players.render(g, pmx, pmy);
            if (model.player == null) {
                Theme.textCentered(g, font, Component.translatable(players.isEmpty()
                        ? "inventorybackups.ui.no_players" : "inventorybackups.ui.loading"), (SIDEBAR + vw) / 2, vh / 2, Theme.MUTED);
            } else {
                renderWho(g);
                timeline.render(g, pmx, pmy);
                inventory.render(g, pmx, pmy);
                details.render(g, pmx, pmy);
            }
        } else {
            search.render(g, pmx, pmy);
        }
        renderStatus(g);
        renderToasts(g);
        if (dialog != null) {
            g.pose().translate(0, 0, 400);
            dialog.render(g, mx, my);
        }
        g.pose().popPose();
        if (tooltip != null && dialog == null) {
            if (tooltip.stack().isEmpty()) {
                g.renderComponentTooltip(font, tooltip.lines(), mouseX, mouseY);
            } else {
                g.renderTooltip(font, tooltip.lines(), tooltip.stack().getTooltipImage(), tooltip.stack(), mouseX, mouseY);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The screen covers everything with its own background.
    }

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, vw, TOP, Theme.TOPBAR);
        Theme.hline(g, 0, TOP - 1, vw, Theme.LINE);
        Theme.text(g, font, title, 8, 7, Theme.ACCENT);
        Theme.vline(g, SIDEBAR - 1, 0, TOP, Theme.LINE);
        tabButton(g, Tab.TIMELINE, Component.translatable("inventorybackups.ui.tab.timeline"), SIDEBAR, mx, my);
        if (canSearch()) {
            tabButton(g, Tab.SEARCH, Component.translatable("inventorybackups.ui.tab.search"), SIDEBAR + 72, mx, my);
        }
        Component clock = Component.translatable("inventorybackups.ui.clock", Formats.clockNow());
        Theme.textRight(g, font, clock, vw - 30, 7, Theme.MUTED);
        Theme.vline(g, vw - 22, 0, TOP, Theme.LINE);
        boolean hover = mx >= vw - 22 && my < TOP;
        Theme.textCentered(g, font, Component.literal("✕"), vw - 11, 7, hover ? Theme.TEXT : Theme.MUTED);
    }

    private void tabButton(GuiGraphics g, Tab which, Component label, int x, int mx, int my) {
        boolean active = tab == which;
        boolean hover = mx >= x && mx < x + 72 && my < TOP;
        if (active) {
            g.fill(x, 0, x + 72, TOP - 1, Theme.PANEL);
            g.fill(x, TOP - 3, x + 72, TOP - 1, Theme.ACCENT);
        }
        Theme.textCentered(g, font, label, x + 36, 7, active || hover ? Theme.TEXT : Theme.MUTED);
        Theme.vline(g, x + 71, 0, TOP, Theme.LINE);
    }

    private void renderWho(GuiGraphics g) {
        PlayerRow row = model.player;
        if (row == null) {
            return;
        }
        int x = SIDEBAR;
        int y = TOP;
        Theme.hline(g, x, y + 25, vw - x, Theme.LINE);
        players.face(g, row.uuid(), x + 8, y + 5);
        Theme.text(g, font, Component.literal(row.name()).withStyle(style -> style.withBold(true)), x + 32, y + 4, Theme.TEXT);
        int nameWidth = font.width(Component.literal(row.name()).withStyle(style -> style.withBold(true)));
        Component status = Component.translatable(row.online() ? "inventorybackups.ui.status.online"
                : "inventorybackups.ui.status.offline");
        int statusColor = row.online() ? Theme.ADDED : Theme.FAINT;
        int px = x + 32 + nameWidth + 5;
        Theme.outline(g, px, y + 3, font.width(status) + 6, 11, statusColor);
        Theme.text(g, font, status, px + 3, y + 5, statusColor);
        Component meta = row.lastDeath() > 0
                ? Component.translatable("inventorybackups.ui.meta_death", model.entries.size(), Formats.time(row.lastDeath()))
                : Component.translatable("inventorybackups.ui.meta", model.entries.size());
        Theme.text(g, font, meta, x + 32, y + 15, vw - x - 200, Theme.FAINT);
        EntryRow selected = model.selected();
        if (selected != null && !selected.dimension().isEmpty()) {
            Theme.textRight(g, font, Component.literal(Formats.place(selected.dimension(), selected.pos().getX(), selected.pos().getY(),
                    selected.pos().getZ())), vw - 8, y + 9, Theme.MUTED);
        }
    }

    private void renderStatus(GuiGraphics g) {
        int y = vh - STATUS;
        g.fill(0, y, vw, vh, Theme.TOPBAR);
        Theme.hline(g, 0, y, vw, Theme.LINE);
        Component hints = Component.translatable(tab == Tab.TIMELINE ? "inventorybackups.ui.hints.timeline"
                : "inventorybackups.ui.hints.search");
        Theme.text(g, font, hints, 8, y + 4, vw - 160, Theme.FAINT);
        List<String> granted = new ArrayList<>();
        for (int i = 0; i < Permissions.ALL.size(); i++) {
            if (can(i)) {
                String node = Permissions.ALL.get(i).getNodeName();
                granted.add(node.substring(node.lastIndexOf('.') + 1));
            }
        }
        Theme.textRight(g, font, Component.literal(String.join(" · ", granted)), vw - 8, y + 4, Theme.FAINT);
    }

    private void renderToasts(GuiGraphics g) {
        int y = vh - STATUS - 6;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast toast = toasts.get(i);
            int w = Math.min(vw - 40, font.width(toast.message()) + 16);
            int x = (vw - w) / 2;
            y -= 18;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            Theme.box(g, x, y, w, 16, Theme.PANEL_2, toast.ok() ? Theme.ADDED : Theme.REMOVED);
            Theme.text(g, font, toast.message(), x + 8, y + 4, w - 16, Theme.TEXT);
            g.pose().popPose();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------------------------

    int virtual(double coordinate) {
        return (int) Math.floor(coordinate / scale);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double mx = mouseX / scale;
        double my = mouseY / scale;
        if (dialog != null) {
            dialog.mouseClicked(mx, my, button);
            return true;
        }
        if (my < TOP) {
            if (mx >= vw - 22) {
                onClose();
            } else if (mx >= SIDEBAR && mx < SIDEBAR + 72) {
                tab = Tab.TIMELINE;
            } else if (mx >= SIDEBAR + 72 && mx < SIDEBAR + 144 && canSearch()) {
                tab = Tab.SEARCH;
                search.focus();
            }
            return true;
        }
        if (tab == Tab.SEARCH) {
            return search.mouseClicked(mx, my, button);
        }
        if (players.mouseClicked(mx, my, button)) {
            return true;
        }
        if (model.player == null) {
            return false;
        }
        return timeline.mouseClicked(mx, my, button) || inventory.mouseClicked(mx, my, button) || details.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dialog == null && tab == Tab.TIMELINE) {
            timeline.mouseDragged(mouseX / scale, mouseY / scale);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        timeline.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double mx = mouseX / scale;
        double my = mouseY / scale;
        if (dialog != null) {
            return true;
        }
        if (tab == Tab.SEARCH) {
            return search.mouseScrolled(mx, my, scrollY);
        }
        return players.mouseScrolled(mx, my, scrollY) || timeline.mouseScrolled(mx, my, scrollY)
                || inventory.mouseScrolled(mx, my, scrollY) || details.mouseScrolled(mx, my, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (dialog != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                dialog = null;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                dialog.confirm();
            }
            return true;
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean typing = tab == Tab.SEARCH ? search.isTyping() : players.isTyping();
        if (ctrl && keyCode == GLFW.GLFW_KEY_F && canSearch()) {
            tab = Tab.SEARCH;
            search.focus();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && !typing) {
            tab = tab == Tab.TIMELINE && canSearch() ? Tab.SEARCH : Tab.TIMELINE;
            return true;
        }
        if (tab == Tab.SEARCH) {
            if (search.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        } else if (players.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (!typing && KeyBindings.OPEN.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            onClose();
            return true;
        }
        if (!typing && tab == Tab.TIMELINE && timelineKey(keyCode, ctrl, shift)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean timelineKey(int keyCode, boolean ctrl, boolean shift) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> step(shift ? -10 : -1);
            case GLFW.GLFW_KEY_RIGHT -> step(shift ? 10 : 1);
            case GLFW.GLFW_KEY_HOME -> select(0);
            case GLFW.GLFW_KEY_END -> select(model.entries.size() - 1);
            case GLFW.GLFW_KEY_SPACE -> togglePlay();
            case GLFW.GLFW_KEY_D -> select(model.previousDeath());
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) {
                    inventory.selectAll();
                } else {
                    timeline.pin(true);
                }
            }
            case GLFW.GLFW_KEY_B -> timeline.pin(false);
            case GLFW.GLFW_KEY_C -> details.cycleBase();
            case GLFW.GLFW_KEY_UP -> players.step(-1);
            case GLFW.GLFW_KEY_DOWN -> players.step(1);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (dialog != null) {
            return true;
        }
        if (tab == Tab.SEARCH) {
            return search.charTyped(codePoint, modifiers);
        }
        return players.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    void closeDialog() {
        dialog = null;
    }

    void playerFace(GuiGraphics g, UUID player, int x, int y) {
        players.face(g, player, x, y);
    }

    InventoryPanel inventoryPanel() {
        return inventory;
    }

    TimelinePanel timelinePanel() {
        return timeline;
    }
}
