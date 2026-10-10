package com.pockyl.inventorybackups.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.backup.BackupService;
import com.pockyl.inventorybackups.index.ItemIndex;
import com.pockyl.inventorybackups.index.SearchQuery;
import com.pockyl.inventorybackups.index.SearchService;
import com.pockyl.inventorybackups.permission.Permissions;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.ui.ChatUI;
import com.pockyl.inventorybackups.ui.SearchChat;
import com.pockyl.inventorybackups.util.PlayerResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.pockyl.inventorybackups.ui.ChatUI.value;
import static com.pockyl.inventorybackups.ui.Texts.tr;

/**
 * {@code /invsearch}: who has an item, who had it, and how it moved between players.
 * <ul>
 *     <li>{@code /invsearch <item> [player|*] [since]}: by item id, since like {@code 2h}, {@code 3d}, {@code 1w}</li>
 *     <li>{@code /invsearch name <text>}, {@code /invsearch ench <enchantment>}: by custom name or enchantment</li>
 *     <li>{@code /invsearch trace <fingerprint>}: one specific stack, from the trace buttons of other results</li>
 *     <li>{@code /invsearch at <time> <item>}: who held the item at that time</li>
 *     <li>{@code /invsearch container <pos> [since]}: who put what into a container and who took it out</li>
 * </ul>
 */
public final class SearchCommands {
    /** Default look-back for transfers and container histories. */
    private static final long DEFAULT_SINCE = 7L * 24 * 3_600_000L;
    private static final String ALL_PLAYERS = "*";

    private SearchCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("invsearch")
                .requires(source -> Permissions.has(source, Permissions.SEARCH))
                .then(Commands.literal("name").then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(context -> search(context, SearchQuery.parse("name:" + StringArgumentType.getString(context, "text")),
                                null, 0, 0))))
                .then(Commands.literal("ench").then(Commands.argument("enchantment", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                context.getSource().registryAccess().registryOrThrow(Registries.ENCHANTMENT).keySet(), builder))
                        .executes(context -> search(context,
                                SearchQuery.parse("ench:" + ResourceLocationArgument.getId(context, "enchantment")), null, 0, 0))))
                .then(Commands.literal("trace").then(Commands.argument("fingerprint", StringArgumentType.word())
                        .executes(context -> search(context, SearchQuery.fingerprint(StringArgumentType.getString(context, "fingerprint")),
                                null, 0, 0))))
                .then(Commands.literal("at").then(Commands.argument("time", StringArgumentType.string())
                        .then(item().executes(SearchCommands::searchAt))))
                .then(Commands.literal("container").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> container(context, ""))
                        .then(Commands.argument("since", StringArgumentType.word())
                                .executes(context -> container(context, StringArgumentType.getString(context, "since"))))))
                .then(item()
                        .executes(context -> searchItem(context, ALL_PLAYERS, ""))
                        .then(Commands.argument("player", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        withAll(context.getSource().getOnlinePlayerNames()), builder))
                                .executes(context -> searchItem(context, StringArgumentType.getString(context, "player"), ""))
                                .then(Commands.argument("since", StringArgumentType.word())
                                        .executes(context -> searchItem(context, StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "since")))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> item() {
        return Commands.argument("item", ResourceLocationArgument.id()).suggests(SearchCommands::suggestItems);
    }

    private static Iterable<String> withAll(Iterable<String> names) {
        List<String> all = new ArrayList<>();
        all.add(ALL_PLAYERS);
        names.forEach(all::add);
        return all;
    }

    /** Items that were ever stored first, then every registered item. */
    private static CompletableFuture<Suggestions> suggestItems(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        BackupStore store = BackupService.store();
        ItemIndex index = BackupService.index();
        if (store == null || index == null) {
            return SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder);
        }
        return store.call(index::itemIds).thenCompose(stored -> {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            stored.stream().sorted().filter(id -> id.startsWith(remaining) || id.startsWith("minecraft:" + remaining))
                    .forEach(builder::suggest);
            return SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder);
        });
    }

    private static int searchItem(CommandContext<CommandSourceStack> context, String playerName, String since) {
        UUID player = null;
        if (!ALL_PLAYERS.equals(playerName)) {
            Optional<PlayerResolver.ResolvedPlayer> resolved = PlayerResolver.resolve(context.getSource().getServer(), playerName);
            if (resolved.isEmpty()) {
                ChatUI.error(context.getSource(), tr("inventorybackups.error.player_not_found", value(playerName)));
                return 0;
            }
            player = resolved.get().uuid();
        }
        OptionalLong window = Durations.parse(since);
        if (window.isEmpty()) {
            ChatUI.error(context.getSource(), tr("inventorybackups.error.bad_duration", value(since)));
            return 0;
        }
        SearchQuery query = SearchQuery.parse(ResourceLocationArgument.getId(context, "item").toString());
        return search(context, query, player, window.getAsLong(), 0);
    }

    private static int searchAt(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "time");
        OptionalLong time = EntrySelector.parse(name);
        if (time.isEmpty() || time.getAsLong() == Long.MAX_VALUE) {
            ChatUI.error(context.getSource(), tr("inventorybackups.error.backup_not_found", value(name)));
            return 0;
        }
        return search(context, SearchQuery.parse(ResourceLocationArgument.getId(context, "item").toString()), null, 0, time.getAsLong());
    }

    /**
     * @param window look-back in milliseconds, 0 for the default
     * @param at     only holders at this time, 0 for any
     */
    private static int search(CommandContext<CommandSourceStack> context, @Nullable SearchQuery query, @Nullable UUID player, long window,
                              long at) {
        CommandSourceStack source = context.getSource();
        BackupStore store = BackupService.store();
        ItemIndex index = BackupService.index();
        if (store == null || index == null) {
            ChatUI.error(source, tr("inventorybackups.error.storage_unavailable"));
            return 0;
        }
        if (query == null) {
            ChatUI.error(source, tr("inventorybackups.error.bad_query"));
            return 0;
        }
        long now = System.currentTimeMillis();
        // Holders are only limited by an explicit look-back, transfers also by the default one: a lifetime of transfers
        // would flood the chat.
        SearchService.Options options = new SearchService.Options(player, window > 0 ? now - window : 0,
                window > 0 ? now - window : now - DEFAULT_SINCE, at, Config.MAX_RESULTS.get(), Config.PROBABLE_LINK_SECONDS.get() * 1000L,
                Config.PROBABLE_LINK_DISTANCE.get());
        store.call(() -> SearchService.search(store, index, query, options)).whenCompleteAsync((result, error) -> {
            if (error != null) {
                InventoryBackups.LOGGER.error("Item search failed", error);
                ChatUI.error(source, tr("inventorybackups.error.storage_unavailable"));
            } else {
                SearchChat.showResult(source, result);
            }
        }, source.getServer());
        return 1;
    }

    private static int container(CommandContext<CommandSourceStack> context, String since) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        OptionalLong window = Durations.parse(since);
        BackupStore store = BackupService.store();
        if (window.isEmpty()) {
            ChatUI.error(source, tr("inventorybackups.error.bad_duration", value(since)));
            return 0;
        }
        if (store == null) {
            ChatUI.error(source, tr("inventorybackups.error.storage_unavailable"));
            return 0;
        }
        String dimension = source.getLevel().dimension().location().toString();
        long from = System.currentTimeMillis() - (window.getAsLong() > 0 ? window.getAsLong() : DEFAULT_SINCE);
        int max = Config.MAX_RESULTS.get();
        store.call(() -> SearchService.containerHistory(store, dimension, pos, from, max)).whenCompleteAsync((flows, error) -> {
            if (error != null) {
                InventoryBackups.LOGGER.error("Container search failed", error);
                ChatUI.error(source, tr("inventorybackups.error.storage_unavailable"));
            } else {
                SearchChat.showContainer(source, ChatUI.position(pos), flows);
            }
        }, source.getServer());
        return 1;
    }

    /** Durations like {@code 30m}, {@code 12h}, {@code 3d} or {@code 2w}; empty text means "not given" (0). */
    public static final class Durations {
        private Durations() {
        }

        public static OptionalLong parse(String text) {
            if (text.isEmpty()) {
                return OptionalLong.of(0);
            }
            if (text.length() < 2) {
                return OptionalLong.empty();
            }
            long unit = switch (Character.toLowerCase(text.charAt(text.length() - 1))) {
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                case 'w' -> 7 * 86_400_000L;
                default -> 0;
            };
            try {
                long amount = Long.parseLong(text.substring(0, text.length() - 1));
                return unit == 0 || amount <= 0 || amount > 100_000 ? OptionalLong.empty() : OptionalLong.of(amount * unit);
            } catch (NumberFormatException e) {
                return OptionalLong.empty();
            }
        }
    }
}
