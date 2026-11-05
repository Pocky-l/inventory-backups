package com.pockyl.inventorybackups.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

import com.pockyl.inventorybackups.backup.BackupService;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.PlayerInfo;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.pockyl.inventorybackups.ui.Texts.tr;

/**
 * Player names for command arguments: everyone online, then the players with backups who are offline (the most recently
 * seen first, with the time in the tooltip), so an admin can find a player who already left by the first letters.
 */
public final class PlayerSuggestions {
    /** Offline players suggested at most; the most recently seen win. */
    private static final int MAX_OFFLINE = 100;

    private PlayerSuggestions() {
    }

    public static CompletableFuture<Suggestions> players(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggest(context, builder, null);
    }

    /** Like {@link #players}, with an extra first choice such as {@code *} for everyone. */
    public static CompletableFuture<Suggestions> playersOr(String extra, CommandContext<CommandSourceStack> context,
                                                          SuggestionsBuilder builder) {
        return suggest(context, builder, extra);
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder,
                                                         String extra) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        if (extra != null && SharedSuggestionProvider.matchesSubStr(typed, extra)) {
            builder.suggest(extra);
        }
        Set<String> online = new HashSet<>();
        for (String name : context.getSource().getOnlinePlayerNames()) {
            online.add(name.toLowerCase(Locale.ROOT));
            if (SharedSuggestionProvider.matchesSubStr(typed, name.toLowerCase(Locale.ROOT))) {
                builder.suggest(name);
            }
        }
        BackupStore store = BackupService.store();
        if (store != null) {
            store.playerInfos().stream()
                    .filter(info -> !info.name().isEmpty() && !online.contains(info.name().toLowerCase(Locale.ROOT)))
                    .filter(info -> SharedSuggestionProvider.matchesSubStr(typed, info.name().toLowerCase(Locale.ROOT)))
                    .sorted(Comparator.comparingLong(PlayerInfo::lastSeen).reversed())
                    .limit(MAX_OFFLINE)
                    .forEach(info -> builder.suggest(info.name(),
                            tr("inventorybackups.suggest.offline", EntrySelector.display(info.lastSeen()))));
        }
        return builder.buildFuture();
    }
}
