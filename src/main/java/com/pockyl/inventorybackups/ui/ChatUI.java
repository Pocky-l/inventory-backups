package com.pockyl.inventorybackups.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.backup.BackupKind;
import com.pockyl.inventorybackups.command.EntrySelector;
import com.pockyl.inventorybackups.storage.Entry;
import com.pockyl.inventorybackups.storage.EntryContext;
import com.pockyl.inventorybackups.storage.Trigger;
import com.pockyl.inventorybackups.util.PlayerResolver.ResolvedPlayer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.pockyl.inventorybackups.ui.Texts.tr;

/**
 * Chat output of the commands: the clickable timeline list with filters and pages, help and status messages. This is
 * the whole interface for admins without the client mod.
 */
public final class ChatUI {
    public static final int PAGE_SIZE = 10;

    private static final String RULE = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    private ChatUI() {
    }

    public static void success(CommandSourceStack source, Component message) {
        source.sendSystemMessage(prefixed(Component.literal("✔ ").append(message).withStyle(ChatFormatting.GREEN)));
    }

    public static void error(CommandSourceStack source, Component message) {
        source.sendSystemMessage(prefixed(Component.literal("✖ ").append(message).withStyle(ChatFormatting.RED)));
    }

    public static void info(CommandSourceStack source, Component message) {
        source.sendSystemMessage(prefixed(message.copy().withStyle(ChatFormatting.GRAY)));
    }

    /** A value inside a message, highlighted. */
    public static Component value(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE);
    }

    /**
     * One page of a player's entries that match the filter.
     *
     * @param entries all entries of the player, newest first
     */
    public static void showList(CommandSourceStack source, BackupKind kind, ResolvedPlayer target, List<Entry> entries, String filter,
                                int page) {
        source.sendSystemMessage(header(tr(key(kind, "header.list"))));
        if (entries.isEmpty()) {
            source.sendSystemMessage(Component.literal("✖ ").append(tr(key(kind, "error.no_backups"))).withStyle(ChatFormatting.RED));
            source.sendSystemMessage(rule());
            return;
        }
        List<Entry> matching = entries.stream().filter(entry -> EntrySelector.matches(entry, filter)).toList();
        if (matching.isEmpty()) {
            source.sendSystemMessage(Component.literal("✖ ")
                    .append(tr("inventorybackups.error.no_match", Component.literal(filter).withStyle(ChatFormatting.YELLOW)))
                    .withStyle(ChatFormatting.RED));
            source.sendSystemMessage(quickFilters(kind, target.name()));
            source.sendSystemMessage(rule());
            return;
        }

        int pages = (matching.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int current = Math.max(1, Math.min(page, pages));
        source.sendSystemMessage(tr("inventorybackups.info.player", value(target.name())).withStyle(ChatFormatting.GRAY)
                .append(" ")
                .append(target.isOnline()
                        ? tr("inventorybackups.info.online").withStyle(ChatFormatting.GREEN)
                        : tr("inventorybackups.info.offline").withStyle(ChatFormatting.DARK_GRAY)));
        source.sendSystemMessage(tr(key(kind, "info.total"), value(matching.size())).withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(quickFilters(kind, target.name()));
        source.sendSystemMessage(Component.empty());

        int from = (current - 1) * PAGE_SIZE;
        for (int i = from; i < Math.min(from + PAGE_SIZE, matching.size()); i++) {
            Entry entry = matching.get(i);
            String args = " " + target.name() + " " + entry.time();
            MutableComponent line = Component.literal((i + 1) + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(describe(entry))
                    .append(" ")
                    .append(button("[👁]", ChatFormatting.GREEN, command(kind, "view" + args), tr("inventorybackups.button.hover.view")))
                    .append(" ")
                    .append(button("[↻]", ChatFormatting.YELLOW, command(kind, "set" + args),
                            tr("inventorybackups.button.hover.restore")))
                    .append(" ")
                    .append(button("[📥]", ChatFormatting.AQUA, command(kind, "copy" + args), tr("inventorybackups.button.hover.copy")));
            Component teleport = teleportButton(entry.context());
            if (teleport != null) {
                line.append(" ").append(teleport);
            }
            source.sendSystemMessage(line);
        }
        if (pages > 1) {
            source.sendSystemMessage(Component.empty());
            source.sendSystemMessage(pagination(kind, target.name(), filter, current, pages));
        }
        source.sendSystemMessage(rule());
    }

    public static void showHelp(CommandSourceStack source, BackupKind kind) {
        source.sendSystemMessage(header(tr(key(kind, "header.help"))));
        ChatFormatting color = kind == BackupKind.ENDER_CHEST ? ChatFormatting.DARK_PURPLE : ChatFormatting.GOLD;
        for (String command : List.of("player", "list", "view", "set", "copy", "undo", "snapshot")) {
            source.sendSystemMessage(tr(key(kind, "help." + command)).withStyle(color));
            source.sendSystemMessage(Component.literal("  ")
                    .append(tr(key(kind, "help." + command + ".desc")).withStyle(ChatFormatting.GRAY)));
        }
        source.sendSystemMessage(Component.empty());
        source.sendSystemMessage(tr("inventorybackups.help.filters").withStyle(ChatFormatting.GRAY));
        for (String example : List.of("month", "day", "type", "page")) {
            source.sendSystemMessage(Component.literal("  ")
                    .append(tr("inventorybackups.help.filter." + example).withStyle(ChatFormatting.WHITE)));
        }
        source.sendSystemMessage(tr("inventorybackups.help.more").withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(rule());
    }

    /** Entry as a readable line: icon, time, trigger and what happened; the details show on hover. */
    public static MutableComponent describe(Entry entry) {
        Trigger trigger = entry.trigger();
        MutableComponent line = Component.literal(icon(trigger) + " " + EntrySelector.display(entry.time()) + " ")
                .withStyle(ChatFormatting.WHITE)
                .append(tr("inventorybackups.trigger." + trigger.key()).withStyle(color(trigger)));
        Component summary = summary(entry);
        if (summary != null) {
            line.append(Component.literal(" ").append(summary).withStyle(ChatFormatting.GRAY));
        }
        return line.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, details(entry))));
    }

    /** Short description of an entry: the death cause, the container, the admin or the number of changed slots. */
    @Nullable
    public static Component summary(Entry entry) {
        EntryContext context = entry.context();
        return switch (entry.trigger()) {
            case DEATH -> context.detail() == null ? null : Component.literal(context.detail());
            case CONTAINER -> context.detail() == null ? changed(entry) : tr("inventorybackups.entry.container", context.detail(),
                    position(context.containerPos()));
            case RESTORE, EDIT, COMMAND -> context.actor() == null ? changed(entry) : tr("inventorybackups.entry.by", context.actor());
            default -> changed(entry);
        };
    }

    public static String icon(Trigger trigger) {
        return switch (trigger) {
            case CHANGE -> "✎";
            case DEATH -> "💀";
            case JOIN -> "➡";
            case QUIT -> "⬅";
            case CONTAINER -> "📦";
            case RESTORE -> "↺";
            case EDIT -> "✍";
            case COMMAND -> "📌";
            case IMPORT -> "📜";
        };
    }

    public static ChatFormatting color(Trigger trigger) {
        return switch (trigger) {
            case CHANGE -> ChatFormatting.YELLOW;
            case DEATH -> ChatFormatting.RED;
            case JOIN -> ChatFormatting.GREEN;
            case QUIT -> ChatFormatting.GRAY;
            case CONTAINER -> ChatFormatting.LIGHT_PURPLE;
            case RESTORE -> ChatFormatting.AQUA;
            case EDIT -> ChatFormatting.GOLD;
            case COMMAND -> ChatFormatting.BLUE;
            case IMPORT -> ChatFormatting.DARK_AQUA;
        };
    }

    public static String position(@Nullable BlockPos pos) {
        return pos == null ? "?" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    @Nullable
    private static Component changed(Entry entry) {
        return entry.changed() == 0 ? null : tr("inventorybackups.entry.changed", entry.changed());
    }

    private static Component details(Entry entry) {
        EntryContext context = entry.context();
        MutableComponent text = tr("inventorybackups.trigger." + entry.trigger().key()).withStyle(color(entry.trigger()))
                .append(Component.literal(" " + EntrySelector.display(entry.time())).withStyle(ChatFormatting.WHITE));
        if (!context.dimension().isEmpty()) {
            text.append("\n").append(tr("inventorybackups.entry.where", context.dimension(), position(context.pos()))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (context.detail() != null && entry.trigger() != Trigger.RESTORE) {
            text.append("\n").append(Component.literal(context.detail()).withStyle(ChatFormatting.GRAY));
        }
        if (entry.trigger() == Trigger.RESTORE && context.detail() != null) {
            try {
                text.append("\n").append(tr("inventorybackups.entry.restored_from",
                        EntrySelector.display(Long.parseLong(context.detail()))).withStyle(ChatFormatting.GRAY));
            } catch (NumberFormatException e) {
                text.append("\n").append(Component.literal(context.detail()).withStyle(ChatFormatting.GRAY));
            }
        }
        if (context.actor() != null) {
            text.append("\n").append(tr("inventorybackups.entry.by", context.actor()).withStyle(ChatFormatting.GRAY));
        }
        text.append("\n").append(tr("inventorybackups.entry.changed", entry.changed()).withStyle(ChatFormatting.DARK_GRAY));
        return text;
    }

    @Nullable
    private static Component teleportButton(EntryContext context) {
        if (context.dimension().isEmpty()) {
            return null;
        }
        String command = "/execute in " + context.dimension() + " run tp @s " + position(context.pos());
        return button("[⌖]", ChatFormatting.DARK_AQUA, command,
                tr("inventorybackups.button.hover.teleport", context.dimension(), position(context.pos())));
    }

    private static Component quickFilters(BackupKind kind, String target) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter day = DateTimeFormatter.ISO_LOCAL_DATE;
        return tr("inventorybackups.filter.quick").withStyle(ChatFormatting.GRAY)
                .append(" ")
                .append(filterButton(kind, target, "today", today.format(day), ChatFormatting.GREEN))
                .append(" ")
                .append(filterButton(kind, target, "yesterday", today.minusDays(1).format(day), ChatFormatting.YELLOW))
                .append(" ")
                .append(filterButton(kind, target, "this_month", today.format(DateTimeFormatter.ofPattern("yyyy-MM")), ChatFormatting.AQUA))
                .append(" ")
                .append(filterButton(kind, target, "deaths", Trigger.DEATH.key(), ChatFormatting.RED))
                .append(" ")
                .append(filterButton(kind, target, "restores", Trigger.RESTORE.key(), ChatFormatting.DARK_AQUA))
                .append(" ")
                .append(filterButton(kind, target, "all", "", ChatFormatting.GRAY));
    }

    private static Component filterButton(BackupKind kind, String target, String id, String filter, ChatFormatting color) {
        MutableComponent label = Component.literal("[").append(tr("inventorybackups.filter." + id)).append("]");
        return withClick(label, color, listCommand(kind, target, filter, 1), tr("inventorybackups.filter.hover." + id));
    }

    private static Component pagination(BackupKind kind, String target, String filter, int current, int pages) {
        MutableComponent previous = Component.literal("◀ ").append(tr("inventorybackups.button.previous"));
        MutableComponent next = tr("inventorybackups.button.next").append(" ▶");
        MutableComponent line = Component.empty();
        line.append(current > 1
                ? withClick(previous, ChatFormatting.GREEN, listCommand(kind, target, filter, current - 1),
                tr("inventorybackups.button.hover.previous", current - 1))
                : previous.withStyle(ChatFormatting.DARK_GRAY));
        line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(tr("inventorybackups.info.page", value(current), value(pages)).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
        line.append(current < pages
                ? withClick(next, ChatFormatting.GREEN, listCommand(kind, target, filter, current + 1),
                tr("inventorybackups.button.hover.next", current + 1))
                : next.withStyle(ChatFormatting.DARK_GRAY));
        return line;
    }

    private static String listCommand(BackupKind kind, String target, String filter, int page) {
        // The filter is quoted so that an empty filter still keeps the page argument in place.
        return command(kind, "list " + target + " \"" + filter + "\" " + page);
    }

    public static String command(BackupKind kind, String arguments) {
        return "/" + kind.id() + " " + arguments;
    }

    public static Component button(String label, ChatFormatting color, String command, Component hover) {
        return withClick(Component.literal(label), color, command, hover);
    }

    private static Component withClick(MutableComponent text, ChatFormatting color, String command, Component hover) {
        return text.withStyle(style -> style.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover.copy().withStyle(color))));
    }

    private static Component prefixed(Component message) {
        return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(tr("inventorybackups.prefix").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(message);
    }

    public static Component header(Component title) {
        return Component.empty()
                .append(rule())
                .append("\n")
                .append(title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    public static Component rule() {
        return Component.literal(RULE).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);
    }

    private static String key(BackupKind kind, String path) {
        return "inventorybackups." + kind.id() + "." + path;
    }
}
