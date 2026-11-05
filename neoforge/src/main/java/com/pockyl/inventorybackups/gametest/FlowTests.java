package com.pockyl.inventorybackups.gametest;

import net.minecraft.util.Prediction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.storage.BackupStore;
import com.pockyl.inventorybackups.storage.Flow;
import com.pockyl.inventorybackups.storage.FlowType;

import java.util.List;
import java.util.UUID;

/**
 * Item flows: container sessions and drops picked up by another player.
 */
public final class FlowTests {
    private FlowTests() {
    }

    /** "Who took the diamonds from my chest": puts and takes are recorded per player and container. */
    @ModTest(timeoutTicks = 200)
    public static void containerSessionsRecordPutsAndTakes(GameTestHelper helper) {
        long start = System.currentTimeMillis();
        BlockPos chestPos = new BlockPos(0, 1, 0);
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        BlockPos absolute = helper.absolutePos(chestPos);
        ServerPlayer owner = TestPlayers.join(helper);
        ServerPlayer thief = TestPlayers.join(helper);

        owner.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 10));
        owner.openMenu(chest);
        owner.getInventory().getItem(0).shrink(4);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 4));
        owner.closeContainer();

        thief.openMenu(chest);
        chest.getItem(0).shrink(3);
        thief.getInventory().setItem(5, new ItemStack(Items.DIAMOND, 3));
        thief.closeContainer();

        helper.startSequence()
                .thenWaitUntil(() -> {
                    Flow put = find(start, owner.getUUID(), FlowType.PUT);
                    helper.assertTrue(put.count() == 4 && "minecraft:diamond".equals(put.item().getStringOr("id", "")), "Put wrong: " + put);
                    helper.assertTrue(absolute.equals(put.containerPos()) && "minecraft:chest".equals(put.container()),
                            "Container not located: " + put);
                    Flow take = find(start, thief.getUUID(), FlowType.TAKE);
                    helper.assertTrue(take.count() == 3 && put.sameContainer(take), "Take wrong: " + take);
                })
                .thenExecute(() -> {
                    TestPlayers.leave(helper, owner);
                    TestPlayers.leave(helper, thief);
                })
                .thenSucceed();
    }

    /** A pickup names the player who dropped the items. */
    @ModTest(timeoutTicks = 200)
    public static void pickupsNameTheDropper(GameTestHelper helper) {
        long start = System.currentTimeMillis();
        ServerPlayer dropper = TestPlayers.join(helper);
        ServerPlayer finder = TestPlayers.join(helper);
        ItemEntity dropped = dropper.drop(new ItemStack(Items.EMERALD, 7), false, Prediction.SERVER_ONLY);
        if (dropped == null) {
            throw GameTests.failure("Nothing was dropped");
        }
        dropped.setNoPickUpDelay();
        dropped.playerTouch(finder);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Flow toss = find(start, dropper.getUUID(), FlowType.TOSS);
                    helper.assertTrue(toss.count() == 7, "Toss count wrong");
                    Flow pickup = find(start, finder.getUUID(), FlowType.PICKUP);
                    helper.assertTrue(pickup.count() == 7 && dropper.getUUID().equals(pickup.source()), "Pickup not linked: " + pickup);
                })
                .thenExecute(() -> {
                    TestPlayers.leave(helper, dropper);
                    TestPlayers.leave(helper, finder);
                })
                .thenSucceed();
    }

    private static Flow find(long since, UUID player, FlowType type) {
        BackupStore store = TestPlayers.store();
        List<Flow> flows = store.call(() -> store.flows(since, Long.MAX_VALUE)).join();
        return flows.stream().filter(flow -> flow.player().equals(player) && flow.type() == type).findFirst()
                .orElseThrow(() -> GameTests.failure("No " + type + " flow of " + player));
    }
}
