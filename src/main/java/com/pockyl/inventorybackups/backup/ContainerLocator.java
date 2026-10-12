package com.pockyl.inventorybackups.backup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Finds out which container a menu shows. Menus do not expose their block, so this looks at the containers behind the
 * slots and falls back to the block the player just right-clicked (double chests, modded blocks without a block entity
 * container).
 */
public final class ContainerLocator {
    /** A right click older than this many ticks is not the one that opened the menu. */
    private static final int CLICK_TICKS = 5;

    private ContainerLocator() {
    }

    /**
     * @param id  block, entity or menu type id
     * @param pos block position, or the entity position for container entities
     */
    public record Location(String id, BlockPos pos) {
    }

    /**
     * Menus of slot mods that only show the player's own inventory with extra slots (Curios, Accessories, Cosmetic
     * Armor, Tool Belt, Backpacked). Moving items there is equipping, not using a container; the slot changes are
     * recorded like any other inventory change.
     */
    private static final Set<String> INVENTORY_SCREENS = Set.of("curios:curios_container", "accessories:original_menu",
            "accessories:experimental_menu", "cosmeticarmorreworked:inventorycosarmor", "toolbelt:belt_slot_container",
            "backpacked:management");

    /** Whether the menu is a slot mod's version of the player's inventory screen. */
    public static boolean isInventoryScreen(AbstractContainerMenu menu) {
        return INVENTORY_SCREENS.contains(menuId(menu));
    }

    /** Whether the slot belongs to the player (inventory or own ender chest) rather than the opened container. */
    public static boolean isPlayerSlot(ServerPlayer player, Slot slot) {
        return slot.container == player.getInventory() || slot.container == player.getEnderChestInventory();
    }

    @Nullable
    public static Location locate(ServerPlayer player, AbstractContainerMenu menu, @Nullable BlockPos clicked, long clickTick) {
        for (Slot slot : menu.slots) {
            if (isPlayerSlot(player, slot)) {
                continue;
            }
            Container container = slot.container;
            if (container instanceof BlockEntity blockEntity) {
                return new Location(blockId(blockEntity), blockEntity.getBlockPos());
            }
            if (container instanceof Entity entity) {
                return new Location(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), entity.blockPosition());
            }
        }
        if (clicked != null && player.serverLevel().getGameTime() - clickTick <= CLICK_TICKS) {
            ResourceLocation block = BuiltInRegistries.BLOCK.getKey(player.serverLevel().getBlockState(clicked).getBlock());
            return new Location(block.toString(), clicked);
        }
        return new Location(menuId(menu), player.blockPosition());
    }

    private static String blockId(BlockEntity blockEntity) {
        return BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()).toString();
    }

    private static String menuId(AbstractContainerMenu menu) {
        try {
            ResourceLocation id = BuiltInRegistries.MENU.getKey(menu.getType());
            return id == null ? "unknown" : id.toString();
        } catch (UnsupportedOperationException e) {
            // Menus without a registered type (the player's own inventory) throw instead of returning null.
            return "unknown";
        }
    }
}
