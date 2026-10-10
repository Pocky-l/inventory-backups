package com.pockyl.inventorybackups.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.command.EntrySelector;
import com.pockyl.inventorybackups.index.SearchResult;
import com.pockyl.inventorybackups.storage.Flow;
import com.pockyl.inventorybackups.storage.FlowType;
import com.pockyl.inventorybackups.storage.ItemCodec;

import java.util.List;
import java.util.UUID;

import static com.pockyl.inventorybackups.ui.ChatUI.value;
import static com.pockyl.inventorybackups.ui.Texts.tr;

/**
 * Chat output of {@code /invsearch}: holders, transfers and container histories with clickable names and times.
 */
public final class SearchChat {
    private static final int SHOWN = 10;

    private SearchChat() {
    }

    public static void showResult(CommandSourceStack source, SearchResult result) {
        MinecraftServer server = source.getServer();
        HolderLookup.Provider registries = server.registryAccess();
        source.sendSystemMessage(ChatUI.header(tr("inventorybackups.search.header", result.query().text())));
        if (result.items().isEmpty()) {
            source.sendSystemMessage(Component.literal("✖ ").append(tr("inventorybackups.search.nothing")).withStyle(ChatFormatting.RED));
            source.sendSystemMessage(ChatUI.rule());
            return;
        }
        MutableComponent items = tr("inventorybackups.search.items").withStyle(ChatFormatting.GRAY);
        for (SearchResult.MatchedItem item : result.items().subList(0, Math.min(SHOWN, result.items().size()))) {
            items.append(" ").append(item(registries, item.sample(), item.itemId(), item.fingerprint()));
        }
        source.sendSystemMessage(items);

        List<SearchResult.Holder> now = result.holders().stream().filter(SearchResult.Holder::holdsNow).toList();
        List<SearchResult.Holder> before = result.holders().stream().filter(h -> !h.holdsNow()).toList();
        source.sendSystemMessage(tr("inventorybackups.search.holders_now", value(now.size())).withStyle(ChatFormatting.GOLD));
        for (SearchResult.Holder holder : now.subList(0, Math.min(SHOWN, now.size()))) {
            String name = name(server, holder.player(), holder.name());
            source.sendSystemMessage(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(player(name, holder.last()))
                    .append(Component.literal(" ×" + holder.count()).withStyle(ChatFormatting.WHITE))
                    .append(" ")
                    .append(tr("inventorybackups.search.since", EntrySelector.display(holder.first())).withStyle(ChatFormatting.GRAY)));
        }
        if (!before.isEmpty()) {
            source.sendSystemMessage(tr("inventorybackups.search.holders_before", value(before.size())).withStyle(ChatFormatting.GOLD));
            for (SearchResult.Holder holder : before.subList(0, Math.min(SHOWN, before.size()))) {
                String name = name(server, holder.player(), holder.name());
                source.sendSystemMessage(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(player(name, holder.last() - 1))
                        .append(" ")
                        .append(tr("inventorybackups.search.seen", EntrySelector.display(holder.first()),
                                EntrySelector.display(holder.last()), holder.max()).withStyle(ChatFormatting.GRAY)));
            }
        }
        source.sendSystemMessage(tr("inventorybackups.search.trail", value(result.trail().size())).withStyle(ChatFormatting.GOLD));
        for (SearchResult.TrailLink link : result.trail().subList(0, Math.min(SHOWN, result.trail().size()))) {
            source.sendSystemMessage(Component.literal("  ").append(link(server, link)));
        }
        if (result.trail().isEmpty()) {
            source.sendSystemMessage(Component.literal("  ").append(tr("inventorybackups.search.no_trail"))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (result.truncated() || now.size() > SHOWN || before.size() > SHOWN || result.trail().size() > SHOWN) {
            source.sendSystemMessage(tr("inventorybackups.search.more").withStyle(ChatFormatting.DARK_GRAY));
        }
        source.sendSystemMessage(ChatUI.rule());
    }

    public static void showContainer(CommandSourceStack source, String position, List<Flow> flows) {
        MinecraftServer server = source.getServer();
        source.sendSystemMessage(ChatUI.header(tr("inventorybackups.search.container_header", position)));
        if (flows.isEmpty()) {
            source.sendSystemMessage(Component.literal("✖ ").append(tr("inventorybackups.search.no_container"))
                    .withStyle(ChatFormatting.RED));
        }
        for (Flow flow : flows.subList(0, Math.min(SHOWN * 2, flows.size()))) {
            String key = flow.type() == FlowType.PUT ? "inventorybackups.search.put" : "inventorybackups.search.took";
            source.sendSystemMessage(Component.literal(EntrySelector.display(flow.time()) + " ").withStyle(ChatFormatting.GRAY)
                    .append(tr(key, player(name(server, flow.player(), flow.playerName()), flow.time()), flow.count(),
                            item(server.registryAccess(), flow.item(), flow.item().getString("id"), null))
                            .withStyle(flow.type() == FlowType.PUT ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }
        source.sendSystemMessage(ChatUI.rule());
    }

    private static Component link(MinecraftServer server, SearchResult.TrailLink link) {
        String from = link.from() == null ? "?" : name(server, link.from(), link.fromName());
        String to = name(server, link.to(), link.toName());
        String how = switch (link.kind()) {
            case DROP -> "inventorybackups.search.link.drop";
            case DEATH_LOOT -> "inventorybackups.search.link.death";
            case CONTAINER -> "inventorybackups.search.link.container";
            case PROBABLE -> "inventorybackups.search.link.probable";
        };
        Object detail = switch (link.kind()) {
            case CONTAINER -> (link.container() == null ? "?" : link.container()) + " " + ChatUI.position(link.containerPos());
            case PROBABLE -> link.gapMillis() / 1000;
            default -> ChatUI.position(link.pos());
        };
        MutableComponent line = Component.literal(EntrySelector.display(link.time()) + " ").withStyle(ChatFormatting.GRAY)
                .append(player(from, link.time() - 1))
                .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                .append(player(to, link.time()));
        if (link.count() > 0) {
            line.append(Component.literal(" ×" + link.count()).withStyle(ChatFormatting.WHITE));
        }
        line.append(" ").append(tr(how, detail).withStyle(link.kind().exact() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return line;
    }

    /** A player name that opens their timeline around the time when clicked. */
    private static Component player(String name, long time) {
        return ChatUI.button(name, ChatFormatting.AQUA, "/inventory view " + name + " " + time,
                tr("inventorybackups.search.hover.player", name, EntrySelector.display(time)));
    }

    /** An item with its full tooltip on hover; distinctive stacks get a trace button. */
    private static Component item(HolderLookup.Provider registries, @Nullable CompoundTag sample, String itemId,
                                  @Nullable String fingerprint) {
        ItemStack stack = sample == null ? ItemStack.EMPTY : ItemCodec.decode(registries, sample, 1);
        MutableComponent label = stack.isEmpty() ? Component.literal(itemId) : stack.getHoverName().copy();
        MutableComponent text = Component.literal("[").append(label).append("]").withStyle(ChatFormatting.WHITE);
        if (!stack.isEmpty()) {
            text.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack))));
        }
        if (fingerprint != null) {
            text.append(ChatUI.button("[⌕]", ChatFormatting.LIGHT_PURPLE, "/invsearch trace " + fingerprint,
                    tr("inventorybackups.search.hover.trace")));
        }
        return text;
    }

    /** The stored name, or the server's profile cache for players imported from 2.x backups. */
    public static String name(MinecraftServer server, UUID player, String stored) {
        if (!stored.isEmpty()) {
            return stored;
        }
        if (server.getProfileCache() != null) {
            return server.getProfileCache().get(player).map(profile -> profile.getName()).orElse(player.toString());
        }
        return player.toString();
    }
}
