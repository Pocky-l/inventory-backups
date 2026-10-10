package com.pockyl.inventorybackups.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import com.pockyl.inventorybackups.backup.BackupKind;
import com.pockyl.inventorybackups.backup.BackupService;
import com.pockyl.inventorybackups.backup.BackupStorage;
import com.pockyl.inventorybackups.util.PlayerResolver.ResolvedPlayer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.pockyl.inventorybackups.ui.Texts.tr;

/**
 * Chat output of the commands: the clickable backup list with filters and pages, help and status messages.
 */
public final class ChatUI {
    public static final int PAGE_SIZE = 10;

    private static final String RULE = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    /** Length of the timestamp that starts every backup name, {@code yyyy-MM-dd-HH-mm-ss}. */
    private static final int TIMESTAMP_LENGTH = 19;

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

    /** Backups of a player matching the filter (a date prefix like 2025-01 or a type like death), one page of them. */
    public static void showList(CommandSourceStack source, BackupKind kind, ResolvedPlayer target, String filter, int page) {
        source.sendSystemMessage(header(tr(key(kind, "header.list"))));
        List<String> all = BackupStorage.list(kind, target.uuid());
        if (all.isEmpty()) {
            source.sendSystemMessage(Component.literal("✖ ").append(tr(key(kind, "error.no_backups"))).withStyle(ChatFormatting.RED));
            source.sendSystemMessage(rule());
            return;
        }
        List<String> matching = all.stream().filter(name -> matches(name, filter)).toList();
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
        source.sendSystemMessage(tr("inventorybackups.info.player", value(target.name())).withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(tr(key(kind, "info.total"), value(matching.size())).withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(quickFilters(kind, target.name()));
        source.sendSystemMessage(Component.empty());

        int from = (current - 1) * PAGE_SIZE;
        for (int i = from; i < Math.min(from + PAGE_SIZE, matching.size()); i++) {
            String name = matching.get(i);
            String args = " " + target.name() + " " + name;
            source.sendSystemMessage(Component.literal((i + 1) + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(describe(name))
                    .append(" ")
                    .append(button("[👁]", ChatFormatting.GREEN, command(kind, "view" + args), tr("inventorybackups.button.hover.view")))
                    .append(" ")
                    .append(button("[↻]", ChatFormatting.YELLOW, command(kind, "set" + args), tr("inventorybackups.button.hover.restore")))
                    .append(" ")
                    .append(button("[📥]", ChatFormatting.AQUA, command(kind, "copy" + args), tr("inventorybackups.button.hover.copy"))));
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
        for (String command : List.of("player", "list", "view", "set", "copy")) {
            source.sendSystemMessage(tr(key(kind, "help." + command)).withStyle(color));
            source.sendSystemMessage(Component.literal("  ").append(tr(key(kind, "help." + command + ".desc")).withStyle(ChatFormatting.GRAY)));
        }
        source.sendSystemMessage(Component.empty());
        source.sendSystemMessage(tr("inventorybackups.help.filters").withStyle(ChatFormatting.GRAY));
        for (String example : List.of("month", "day", "type", "page")) {
            source.sendSystemMessage(Component.literal("  ").append(tr("inventorybackups.help.filter." + example).withStyle(ChatFormatting.WHITE)));
        }
        source.sendSystemMessage(rule());
    }

    /** Whether a backup name passes a list filter: a date prefix or a backup type. */
    public static boolean matches(String name, String filter) {
        return filter.isEmpty() || name.startsWith(filter) || name.endsWith("-" + filter);
    }

    /** Backup name as a readable line: type icon, timestamp and type label. */
    private static MutableComponent describe(String name) {
        String type = name.length() > TIMESTAMP_LENGTH ? name.substring(TIMESTAMP_LENGTH + 1) : "";
        String timestamp = name.length() > TIMESTAMP_LENGTH ? name.substring(0, TIMESTAMP_LENGTH) : name;
        String icon;
        ChatFormatting color;
        switch (type) {
            case BackupService.DEATH -> {
                icon = "💀 ";
                color = ChatFormatting.RED;
            }
            case BackupService.JOIN -> {
                icon = "➡ ";
                color = ChatFormatting.GREEN;
            }
            case BackupService.QUIT -> {
                icon = "⬅ ";
                color = ChatFormatting.GRAY;
            }
            case BackupService.OPEN -> {
                icon = "📦 ";
                color = ChatFormatting.LIGHT_PURPLE;
            }
            default -> {
                type = "auto";
                icon = "⏰ ";
                color = ChatFormatting.YELLOW;
            }
        }
        return Component.literal(icon + timestamp + " ").withStyle(ChatFormatting.WHITE)
                .append(tr("inventorybackups.type." + type).withStyle(color));
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
                .append(filterButton(kind, target, "deaths", BackupService.DEATH, ChatFormatting.RED))
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

    private static String command(BackupKind kind, String arguments) {
        return "/" + kind.id() + " " + arguments;
    }

    private static Component button(String label, ChatFormatting color, String command, Component hover) {
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

    private static Component header(Component title) {
        return Component.empty()
                .append(rule())
                .append("\n")
                .append(title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    private static Component rule() {
        return Component.literal(RULE).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);
    }

    private static String key(BackupKind kind, String path) {
        return "inventorybackups." + kind.id() + "." + path;
    }
}
