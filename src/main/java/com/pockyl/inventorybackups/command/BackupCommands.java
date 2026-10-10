package com.pockyl.inventorybackups.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
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
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.BackupKind;
import com.pockyl.inventorybackups.backup.BackupService;
import com.pockyl.inventorybackups.backup.ChangeTracker;
import com.pockyl.inventorybackups.backup.PerfStats;
import com.pockyl.inventorybackups.backup.Restorer;
import com.pockyl.inventorybackups.menu.LivePlayerMenu;
import com.pockyl.inventorybackups.menu.ReadOnlyChestMenu;
import com.pockyl.inventorybackups.permission.Permissions;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.Entry;
import com.pockyl.inventorybackups.storage.EntryContext;
import com.pockyl.inventorybackups.storage.StateView;
import com.pockyl.inventorybackups.storage.Trigger;
import com.pockyl.inventorybackups.ui.ChatUI;
import com.pockyl.inventorybackups.util.PlayerResolver;
import com.pockyl.inventorybackups.util.PlayerResolver.ResolvedPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.pockyl.inventorybackups.ui.ChatUI.value;
import static com.pockyl.inventorybackups.ui.Texts.tr;

/**
 * {@code /inventory} and {@code /enderchest}: the chat interface for admins without the client mod. Each sub-command
 * needs its own permission node (see {@link Permissions}). Both work on the
 * same timeline; {@code /enderchest} shows and restores the ender chest part of it. Reads run on the storage thread
 * and answer when they are done, so a large history never stalls the server.
 */
public final class BackupCommands {
    private static final int SUGGESTED_ENTRIES = 30;
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
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(kind.id())
                .requires(Permissions::hasAny)
                .executes(context -> {
                    ChatUI.showHelp(context.getSource(), kind);
                    return 1;
                })
                .then(Commands.literal("player").requires(needs(Permissions.EDIT)).then(target()
                        .executes(context -> openLive(context, kind))))
                .then(Commands.literal("list").requires(needs(Permissions.VIEW)).then(target()
                        .executes(context -> list(context, kind, "", 1))
                        .then(Commands.argument("filter", StringArgumentType.string())
                                .executes(context -> list(context, kind, StringArgumentType.getString(context, "filter"), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> list(context, kind, StringArgumentType.getString(context, "filter"),
                                                IntegerArgumentType.getInteger(context, "page")))))))
                .then(Commands.literal("view").requires(needs(Permissions.VIEW)).then(target().then(backup()
                        .executes(context -> view(context, kind)))))
                .then(Commands.literal("set").requires(needs(Permissions.RESTORE)).then(target().then(backup()
                        .executes(context -> restore(context, kind)))))
                .then(Commands.literal("copy").requires(needs(Permissions.RESTORE)).then(target().then(backup()
                        .executes(context -> copy(context, kind)))))
                .then(Commands.literal("undo").requires(needs(Permissions.RESTORE)).then(target()
                        .executes(BackupCommands::undo)))
                .then(Commands.literal("snapshot").requires(needs(Permissions.VIEW)).then(target()
                        .executes(BackupCommands::snapshot)));
        if (kind == BackupKind.INVENTORY) {
            root.then(Commands.literal("stats").requires(needs(Permissions.VIEW))
                    .executes(BackupCommands::stats));
        }
        return root;
    }

    private static Predicate<CommandSourceStack> needs(PermissionNode<Boolean> node) {
        return source -> Permissions.has(source, node);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> target() {
        return Commands.argument("target", StringArgumentType.string()).suggests(PLAYERS);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> backup() {
        return Commands.argument("backup", StringArgumentType.string()).suggests(BackupCommands::suggestEntries);
    }

    /** The newest entries of the target, with their time and trigger as tooltip. */
    private static CompletableFuture<Suggestions> suggestEntries(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        BackupStore store = BackupService.store();
        Optional<ResolvedPlayer> target = PlayerResolver.resolve(context.getSource().getServer(),
                StringArgumentType.getString(context, "target"));
        if (store == null || target.isEmpty()) {
            return builder.buildFuture();
        }
        return store.call(() -> newestFirst(store.entries(target.get().uuid(), 0, Long.MAX_VALUE))).thenApply(entries -> {
            builder.suggest(EntrySelector.LATEST);
            for (Entry entry : entries.subList(0, Math.min(SUGGESTED_ENTRIES, entries.size()))) {
                String time = Long.toString(entry.time());
                if (time.startsWith(builder.getRemaining())) {
                    builder.suggest(time, Component.literal(EntrySelector.display(entry.time()) + " ")
                            .append(tr("inventorybackups.trigger." + entry.trigger().key())));
                }
            }
            return builder.build();
        });
    }

    private static int list(CommandContext<CommandSourceStack> context, BackupKind kind, String filter, int page) {
        Optional<ResolvedPlayer> target = resolveTarget(context, kind);
        if (target.isEmpty()) {
            return 0;
        }
        ResolvedPlayer player = target.get();
        query(context.getSource(), store -> newestFirst(store.entries(player.uuid(), 0, Long.MAX_VALUE)),
                entries -> ChatUI.showList(context.getSource(), kind, player, entries, filter, page));
        return 1;
    }

    private static int view(CommandContext<CommandSourceStack> context, BackupKind kind) throws CommandSyntaxException {
        ServerPlayer executor = context.getSource().getPlayerOrException();
        withState(context, kind, (target, state) -> {
            int size = kind.previewRows() * 9;
            Container preview = new SimpleContainer(size);
            state.items().int2ObjectEntrySet().forEach(slot -> {
                int chestSlot = kind.chestSlot(slot.getIntKey());
                if (chestSlot >= 0) {
                    preview.setItem(chestSlot, slot.getValue());
                }
            });
            String time = EntrySelector.display(state.entry().time());
            Component title = tr(key(kind, "preview.title"), target.name(), time);
            executor.openMenu(new SimpleMenuProvider((id, inventory, player) ->
                    new ReadOnlyChestMenu(menuType(kind), id, inventory, preview, kind.previewRows()), title));
            ChatUI.info(context.getSource(), tr(key(kind, "info.viewing"), value(time)));
        });
        return 1;
    }

    private static int restore(CommandContext<CommandSourceStack> context, BackupKind kind) {
        withState(context, kind, (target, state) -> {
            ServerPlayer player = onlineOrError(context, target, "inventorybackups.error.player_offline_cannot_restore");
            if (player == null) {
                return;
            }
            String time = EntrySelector.display(state.entry().time());
            Restorer.restore(player, state, kind == BackupKind.INVENTORY, kind == BackupKind.ENDER_CHEST,
                    context.getSource().getTextName());
            ChatUI.success(context.getSource(), tr(key(kind, "success.restored"), value(time), value(player.getScoreboardName()))
                    .append(" ")
                    .append(ChatUI.button("[↶]", ChatFormatting.YELLOW, ChatUI.command(kind, "undo " + player.getScoreboardName()),
                            tr("inventorybackups.button.hover.undo"))));
            player.sendSystemMessage(tr(key(kind, "info.restored"), value(time)).withStyle(ChatFormatting.GRAY));
        });
        return 1;
    }

    private static int copy(CommandContext<CommandSourceStack> context, BackupKind kind) throws CommandSyntaxException {
        ServerPlayer executor = context.getSource().getPlayerOrException();
        withState(context, kind, (target, state) -> {
            List<ItemStack> stacks = new ArrayList<>();
            state.items().int2ObjectEntrySet().forEach(slot -> {
                if (kind.covers(slot.getIntKey())) {
                    stacks.add(slot.getValue());
                }
            });
            Restorer.give(executor, stacks);
            ChatUI.success(context.getSource(), tr(key(kind, "success.copied"), value(EntrySelector.display(state.entry().time())),
                    value(target.name())));
        });
        return 1;
    }

    /** Puts back the state from right before the target's newest restore. */
    private static int undo(CommandContext<CommandSourceStack> context) {
        Optional<ResolvedPlayer> target = resolveTarget(context, BackupKind.INVENTORY);
        if (target.isEmpty()) {
            return 0;
        }
        ResolvedPlayer resolved = target.get();
        query(context.getSource(), store -> {
            List<Entry> entries = newestFirst(store.entries(resolved.uuid(), 0, Long.MAX_VALUE));
            return entries.stream().filter(entry -> entry.trigger() == Trigger.RESTORE).findFirst()
                    .flatMap(restore -> store.stateAt(resolved.uuid(), restore.time() - 1));
        }, state -> {
            if (state.isEmpty()) {
                ChatUI.error(context.getSource(), tr("inventorybackups.error.no_undo", value(resolved.name())));
                return;
            }
            ServerPlayer player = onlineOrError(context, resolved, "inventorybackups.error.player_offline_cannot_restore");
            if (player != null) {
                Restorer.restore(player, state.get(), true, Config.ENDER_CHEST_ENABLED.get(), context.getSource().getTextName());
                ChatUI.success(context.getSource(), tr("inventorybackups.success.undone", value(player.getScoreboardName()),
                        value(EntrySelector.display(state.get().entry().time()))));
            }
        });
        return 1;
    }

    private static int snapshot(CommandContext<CommandSourceStack> context) {
        Optional<ResolvedPlayer> target = resolveTarget(context, BackupKind.INVENTORY);
        if (target.isEmpty()) {
            return 0;
        }
        ServerPlayer player = onlineOrError(context, target.get(), "inventorybackups.error.player_offline_cannot_view");
        if (player == null) {
            return 0;
        }
        ChangeTracker.capture(player, Trigger.COMMAND, EntryContext.of(player).withActor(context.getSource().getTextName()));
        ChatUI.success(context.getSource(), tr("inventorybackups.success.snapshot", value(player.getScoreboardName())));
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> context) {
        BackupStore store = BackupService.store();
        if (store == null) {
            ChatUI.error(context.getSource(), tr("inventorybackups.error.storage_unavailable"));
            return 0;
        }
        PerfStats.Snapshot perf = PerfStats.snapshot();
        BackupStore.Stats written = store.stats();
        CommandSourceStack source = context.getSource();
        source.sendSystemMessage(ChatUI.header(tr("inventorybackups.stats.header")));
        source.sendSystemMessage(tr("inventorybackups.stats.players", value(ChangeTracker.trackedPlayers()))
                .withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(tr("inventorybackups.stats.main_thread", value(String.format("%.4f", perf.millisPerPlayerTick())),
                value(String.format("%.3f", perf.millisPerCapture()))).withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(tr("inventorybackups.stats.written", value(written.entries()), value(written.bytes() / 1024),
                value(written.bytesPerChange()), value(written.skipped())).withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(ChatUI.rule());
        return 1;
    }

    private static int openLive(CommandContext<CommandSourceStack> context, BackupKind kind) throws CommandSyntaxException {
        ServerPlayer executor = context.getSource().getPlayerOrException();
        Optional<ResolvedPlayer> target = resolveTarget(context, kind);
        if (target.isEmpty()) {
            return 0;
        }
        ServerPlayer player = onlineOrError(context, target.get(), "inventorybackups.error.player_offline_cannot_view");
        if (player == null) {
            return 0;
        }
        LivePlayerMenu.open(executor, player, kind == BackupKind.ENDER_CHEST, tr(key(kind, "player.title"), player.getScoreboardName()));
        ChatUI.info(context.getSource(), tr(key(kind, "info.viewing_player"), value(player.getScoreboardName())));
        return 1;
    }

    /** Resolves target and backup arguments, loads the state on the storage thread and continues on the server thread. */
    private static void withState(CommandContext<CommandSourceStack> context, BackupKind kind, StateAction action) {
        Optional<ResolvedPlayer> target = resolveTarget(context, kind);
        if (target.isEmpty()) {
            return;
        }
        String name = StringArgumentType.getString(context, "backup");
        OptionalLong time = EntrySelector.parse(name);
        if (time.isEmpty()) {
            ChatUI.error(context.getSource(), tr("inventorybackups.error.backup_not_found", value(name)));
            return;
        }
        ResolvedPlayer player = target.get();
        query(context.getSource(), store -> store.stateAt(player.uuid(), time.getAsLong()), state -> {
            if (state.isEmpty()) {
                ChatUI.error(context.getSource(), tr("inventorybackups.error.backup_not_found", value(name)));
            } else {
                action.run(player, state.get());
            }
        });
    }

    private interface StateAction {
        void run(ResolvedPlayer target, StateView state);
    }

    /** Runs a read on the storage thread and hands the result to the server thread. */
    private static <T> void query(CommandSourceStack source, Function<BackupStore, T> read, Consumer<T> then) {
        BackupStore store = BackupService.store();
        if (store == null) {
            ChatUI.error(source, tr("inventorybackups.error.storage_unavailable"));
            return;
        }
        store.call(() -> read.apply(store)).whenCompleteAsync((result, error) -> {
            if (error != null) {
                InventoryBackups.LOGGER.error("Backup query failed", error);
                ChatUI.error(source, tr("inventorybackups.error.storage_unavailable"));
            } else {
                then.accept(result);
            }
        }, source.getServer());
    }

    private static List<Entry> newestFirst(List<Entry> entries) {
        List<Entry> copy = new ArrayList<>(entries);
        Collections.reverse(copy);
        return copy;
    }

    @Nullable
    private static ServerPlayer onlineOrError(CommandContext<CommandSourceStack> context, ResolvedPlayer target, String errorKey) {
        // Look the player up again: they may have left while the state was loading.
        ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayer(target.uuid());
        if (player == null) {
            ChatUI.error(context.getSource(), tr(errorKey, value(target.name())));
        }
        return player;
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

    private static MenuType<?> menuType(BackupKind kind) {
        return kind.previewRows() == 3 ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x6;
    }

    private static String key(BackupKind kind, String path) {
        return "inventorybackups." + kind.id() + "." + path;
    }
}
