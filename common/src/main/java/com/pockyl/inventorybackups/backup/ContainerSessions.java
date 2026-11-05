package com.pockyl.inventorybackups.backup;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.menu.LivePlayerMenu;
import com.pockyl.inventorybackups.menu.ReadOnlyChestMenu;
import com.pockyl.inventorybackups.storage.FlowDraft;
import com.pockyl.inventorybackups.storage.FlowType;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * What players put into and take out of containers. The contents of the container and of the player are counted when
 * the menu opens and when it closes; an item counts as put in only when the container gained it and the player lost
 * it, and as taken only the other way round. Another player using the same chest at the same time therefore does not
 * show up in this player's session.
 */
final class ContainerSessions {
    private static final Map<AbstractContainerMenu, Session> OPEN = new IdentityHashMap<>();

    private ContainerSessions() {
    }

    private record Session(ContainerLocator.Location location, @Nullable ItemTally container, @Nullable ItemTally player) {
    }

    static void open(ServerPlayer player, AbstractContainerMenu menu, ContainerLocator.Location location) {
        boolean track = Config.CONTAINER_SESSIONS.get() && !(menu instanceof LivePlayerMenu) && !(menu instanceof ReadOnlyChestMenu);
        OPEN.put(menu, track ? new Session(location, containerTally(player, menu), playerTally(player, menu))
                : new Session(location, null, null));
    }

    /** Ends the session of a menu and returns where the container was, or null for menus without a session. */
    @Nullable
    static ContainerLocator.Location close(ServerPlayer player, AbstractContainerMenu menu, long time) {
        Session session = OPEN.remove(menu);
        if (session == null) {
            return null;
        }
        if (session.container() != null && session.player() != null) {
            List<FlowDraft> flows = flows(player, session, containerTally(player, menu), playerTally(player, menu), time);
            FlowTracker.submit(flows);
        }
        return session.location();
    }

    static void clear() {
        OPEN.clear();
    }

    private static List<FlowDraft> flows(ServerPlayer player, Session session, ItemTally containerNow, ItemTally playerNow, long time) {
        List<FlowDraft> flows = new ArrayList<>();
        ContainerLocator.Location location = session.location();
        for (ItemStack prototype : session.container().union(containerNow)) {
            int containerDelta = containerNow.count(prototype) - session.container().count(prototype);
            int playerDelta = playerNow.count(prototype) - session.player().count(prototype);
            FlowType type = null;
            int count = 0;
            if (containerDelta > 0 && playerDelta < 0) {
                type = FlowType.PUT;
                count = Math.min(containerDelta, -playerDelta);
            } else if (containerDelta < 0 && playerDelta > 0) {
                type = FlowType.TAKE;
                count = Math.min(-containerDelta, playerDelta);
            }
            if (type != null) {
                flows.add(new FlowDraft(time, type, player.getUUID(), player.getScoreboardName(), prototype.copyWithCount(count),
                        player.level().dimension().location().toString(), player.blockPosition(), null, null, location.id(),
                        location.pos()));
            }
        }
        return flows;
    }

    private static ItemTally containerTally(ServerPlayer player, AbstractContainerMenu menu) {
        ItemTally tally = new ItemTally();
        for (Slot slot : menu.slots) {
            if (!ContainerLocator.isPlayerSlot(player, slot)) {
                tally.add(slot.getItem());
            }
        }
        return tally;
    }

    /** The player's inventory plus the stack on the cursor, which goes back to the inventory when the menu closes. */
    private static ItemTally playerTally(ServerPlayer player, AbstractContainerMenu menu) {
        ItemTally tally = new ItemTally();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            tally.add(inventory.getItem(i));
        }
        tally.add(menu.getCarried());
        return tally;
    }
}
