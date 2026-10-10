package com.pockyl.inventorybackups.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.BackupData;
import com.pockyl.inventorybackups.backup.BackupKind;
import com.pockyl.inventorybackups.backup.BackupStorage;
import com.pockyl.inventorybackups.backup.PlayerSlots;
import com.pockyl.inventorybackups.menu.LivePlayerMenu;
import com.pockyl.inventorybackups.menu.ReadOnlyChestMenu;
import com.pockyl.inventorybackups.ui.ChatUI;
import com.pockyl.inventorybackups.util.PlayerResolver;
import com.pockyl.inventorybackups.util.PlayerResolver.ResolvedPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.pockyl.inventorybackups.ui.ChatUI.value;
import static com.pockyl.inventorybackups.ui.Texts.tr;

/**
 * {@code /inventory} and {@code /enderchest}: the same sub-commands for both kinds of backup, operators only.
 */
public final class BackupCommands {
    private static final int PERMISSION_LEVEL = 2;
    private static final SuggestionProvider<CommandSourceStack> PLAYERS =
            (context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder);

    private BackupCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (BackupKind kind : BackupKind.values()) {
            dispatcher.register(tree(kind));
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tree(BackupKind kind) {
        return Commands.literal(kind.id())
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .executes(context -> {
                    ChatUI.showHelp(context.getSource(), kind);
                    return 1;
                })
                .then(Commands.literal("player").then(target()
                        .executes(context -> openLive(context, kind))))
                .then(Commands.literal("list").then(target()
                        .executes(context -> list(context, kind, "", 1))
                        .then(Commands.argument("filter", StringArgumentType.string())
                                .executes(context -> list(context, kind, StringArgumentType.getString(context, "filter"), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> list(context, kind, StringArgumentType.getString(context, "filter"),
                                                IntegerArgumentType.getInteger(context, "page")))))))
                .then(Commands.literal("view").then(target().then(backup(kind)
                        .executes(context -> view(context, kind)))))
                .then(Commands.literal("set").then(target().then(backup(kind)
                        .executes(context -> restore(context, kind)))))
                .then(Commands.literal("copy").then(target().then(backup(kind)
                        .executes(context -> copy(context, kind)))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> target() {
        return Commands.argument("target", StringArgumentType.string()).suggests(PLAYERS);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> backup(BackupKind kind) {
        return Commands.argument("backup", StringArgumentType.string()).suggests((context, builder) -> {
            Optional<ResolvedPlayer> target = PlayerResolver.resolve(context.getSource().getServer(),
                    StringArgumentType.getString(context, "target"));
            List<String> names = target.map(player -> BackupStorage.list(kind, player.uuid())).orElse(List.of());
            return SharedSuggestionProvider.suggest(names, builder);
        });
    }

    private static int list(CommandContext<CommandSourceStack> context, BackupKind kind, String filter, int page) {
        Optional<ResolvedPlayer> target = resolveTarget(context, kind);
        if (target.isEmpty()) {
            return 0;
        }
        ChatUI.showList(context.getSource(), kind, target.get(), filter, page);
        return 1;
    }

    private static int view(CommandContext<CommandSourceStack> context, BackupKind kind) throws CommandSyntaxException {
        ServerPlayer executor = context.getSource().getPlayerOrException();
        Optional<ResolvedPlayer> target = resolveTarget(context, kind);
        String name = StringArgumentType.getString(context, "backup");
        Optional<BackupData> backup = target.flatMap(player -> loadBackup(context, kind, player, name));
        if (backup.isEmpty()) {
            return 0;
        }
        int size = kind.previewRows() * 9;
        Container preview = new SimpleContainer(size);
        backup.get().decode(executor.registryAccess()).forEach((index, stack) -> {
            int slot = PlayerSlots.screenSlot(kind, index, size);
            if (slot >= 0) {
                preview.setItem(slot, stack);
            }
        });
        Component title = tr(key(kind, "preview.title"), target.get().name(), name);
        executor.openMenu(new SimpleMenuProvider((id, inventory, player) ->
                new ReadOnlyChestMenu(menuType(kind), id, inventory, preview, kind.previewRows()), title));
        ChatUI.info(context.getSource(), tr(key(kind, "info.viewing"), value(name)));
        return 1;
    }

    private static int restore(CommandContext<CommandSourceStack> context, BackupKind kind) {
        Optional<ResolvedPlayer> target = resolveTarget(context, kind);
        String name = StringArgumentType.getString(context, "backup");
        Optional<BackupData> backup = target.flatMap(player -> loadBackup(context, kind, player, name));
        if (backup.isEmpty()) {
            return 0;
        }
        ServerPlayer player = target.get().online();
        if (player == null) {
            ChatUI.error(context.getSource(), tr("inventorybackups.error.player_offline_cannot_restore", value(target.get().name())));
            return 0;
        }
        PlayerSlots.restore(kind, player, backup.get().decode(player.registryAccess()));
        InventoryBackups.LOGGER.info("{} restored {} backup {} of {}", context.getSource().getTextName(), kind.id(), name,
                player.getScoreboardName());
        ChatUI.success(context.getSource(), tr(key(kind, "success.restored"), value(name), value(player.getScoreboardName())));
        player.sendSystemMessage(tr(key(kind, "info.restored"), value(name)).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int copy(CommandContext<CommandSourceStack> context, BackupKind kind) throws CommandSyntaxException {
        ServerPlayer executor = context.getSource().getPlayerOrException();
        Optional<ResolvedPlayer> target = resolveTarget(context, kind);
        String name = StringArgumentType.getString(context, "backup");
        Optional<BackupData> backup = target.flatMap(player -> loadBackup(context, kind, player, name));
        if (backup.isEmpty()) {
            return 0;
        }
        Map<Integer, ItemStack> items = backup.get().decode(executor.registryAccess());
        for (ItemStack stack : items.values()) {
            // add() shrinks the stack by what fits; the rest drops at the executor's feet.
            ItemStack remaining = stack.copy();
            if (!executor.getInventory().add(remaining) && !remaining.isEmpty()) {
                executor.drop(remaining, false);
            }
        }
        ChatUI.success(context.getSource(), tr(key(kind, "success.copied"), value(name), value(target.get().name())));
        return 1;
    }

    private static int openLive(CommandContext<CommandSourceStack> context, BackupKind kind) throws CommandSyntaxException {
        ServerPlayer executor = context.getSource().getPlayerOrException();
        Optional<ResolvedPlayer> target = resolveTarget(context, kind);
        if (target.isEmpty()) {
            return 0;
        }
        ServerPlayer player = target.get().online();
        if (player == null) {
            ChatUI.error(context.getSource(), tr("inventorybackups.error.player_offline_cannot_view", value(target.get().name())));
            return 0;
        }
        Container contents = kind == BackupKind.ENDER_CHEST ? player.getEnderChestInventory() : player.getInventory();
        Component title = tr(key(kind, "player.title"), player.getScoreboardName());
        executor.openMenu(new SimpleMenuProvider((id, inventory, viewer) ->
                new LivePlayerMenu(menuType(kind), id, inventory, contents, kind.previewRows(), player), title));
        ChatUI.info(context.getSource(), tr(key(kind, "info.viewing_player"), value(player.getScoreboardName())));
        return 1;
    }

    /** Resolves the target argument, reporting an unknown player or disabled ender chest backups. */
    private static Optional<ResolvedPlayer> resolveTarget(CommandContext<CommandSourceStack> context, BackupKind kind) {
        if (kind == BackupKind.ENDER_CHEST && !Config.ENDER_CHEST_ENABLED.get()) {
            ChatUI.error(context.getSource(), tr("inventorybackups.error.enderchest_disabled"));
            return Optional.empty();
        }
        String name = StringArgumentType.getString(context, "target");
        Optional<ResolvedPlayer> target = PlayerResolver.resolve(context.getSource().getServer(), name);
        if (target.isEmpty()) {
            ChatUI.error(context.getSource(), tr("inventorybackups.error.player_not_found", value(name)));
        }
        return target;
    }

    private static Optional<BackupData> loadBackup(CommandContext<CommandSourceStack> context, BackupKind kind, ResolvedPlayer target,
                                                   String name) {
        Optional<BackupData> backup = BackupStorage.load(kind, target.uuid(), name);
        if (backup.isEmpty()) {
            ChatUI.error(context.getSource(), tr("inventorybackups.error.backup_not_found", value(name)));
        }
        return backup;
    }

    private static MenuType<?> menuType(BackupKind kind) {
        return kind.previewRows() == 3 ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x6;
    }

    private static String key(BackupKind kind, String path) {
        return "inventorybackups." + kind.id() + "." + path;
    }
}
