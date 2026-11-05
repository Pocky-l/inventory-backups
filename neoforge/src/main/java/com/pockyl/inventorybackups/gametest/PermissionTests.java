package com.pockyl.inventorybackups.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.server.permission.PermissionAPI;

import com.pockyl.inventorybackups.Config;
import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.permission.Permissions;

/**
 * Permission nodes: registered with the PermissionAPI and falling back to the operator level without a permission mod.
 */
@GameTestHolder(InventoryBackups.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PermissionTests {
    private PermissionTests() {
    }

    @GameTest(template = "empty")
    public static void nodesFallBackToOperatorLevel(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = TestPlayers.join(helper);
        try {
            for (Permissions.Node node : Permissions.ALL) {
                helper.assertTrue(PermissionAPI.getRegisteredNodes().stream().anyMatch(registered -> registered.getNodeName().equals(node.nodeName())),
                        "Node not registered: " + node.nodeName());
                helper.assertTrue(node.nodeName().startsWith("inventorybackups."), "Unexpected node name " + node.nodeName());
                helper.assertFalse(Permissions.has(player, node), "Non-operator granted " + node.nodeName());
            }
            helper.assertFalse(Permissions.hasAny(player.createCommandSourceStack()), "Non-operator can use the commands");

            // The test server grants operators no level, so lower the required level instead of making the player an op.
            Config.PERMISSION_LEVEL.set(0);
            for (Permissions.Node node : Permissions.ALL) {
                helper.assertTrue(Permissions.has(player, node), "Configured level ignored for " + node.nodeName());
            }
            Config.PERMISSION_LEVEL.set(2);
            helper.assertFalse(Permissions.has(player, Permissions.RESTORE), "Raised level still granted");

            helper.assertTrue(Permissions.has(server.createCommandSourceStack(), Permissions.EDIT), "Console denied");
            helper.succeed();
        } finally {
            Config.PERMISSION_LEVEL.set(2);
            TestPlayers.leave(helper, player);
        }
    }
}
