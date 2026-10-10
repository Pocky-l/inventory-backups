package com.pockyl.inventorybackups.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import com.pockyl.inventorybackups.InventoryBackups;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Optional Curios support. Curios is reached through reflection, so the mod neither compiles against it nor needs it
 * at runtime. Curio slots are numbered from {@link #FIRST_SLOT} in the order Curios lists them.
 */
public final class CuriosCompat {
    public static final int FIRST_SLOT = 1000;

    private static final String MOD_ID = "curios";
    private static Boolean loaded;

    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(MOD_ID);
        }
        return loaded;
    }

    public static boolean isCurioSlot(int index) {
        return index >= FIRST_SLOT;
    }

    /** Non-empty curio slots of the player; empty when Curios is absent. */
    public static Map<Integer, ItemStack> collect(Player player) {
        Map<Integer, ItemStack> items = new HashMap<>();
        if (!isLoaded()) {
            return items;
        }
        try {
            int index = FIRST_SLOT;
            for (Object handler : stackHandlers(player)) {
                int slots = (int) handler.getClass().getMethod("getSlots").invoke(handler);
                Method get = handler.getClass().getMethod("getStackInSlot", int.class);
                for (int i = 0; i < slots; i++, index++) {
                    ItemStack stack = (ItemStack) get.invoke(handler, i);
                    if (!stack.isEmpty()) {
                        items.put(index, stack.copy());
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            InventoryBackups.LOGGER.error("Could not read Curios slots of {}", player.getScoreboardName(), e);
        }
        return items;
    }

    /** Replaces every curio slot of the player with the backup contents (slots missing from it are cleared). */
    public static void restore(Player player, Map<Integer, ItemStack> items) {
        if (!isLoaded()) {
            return;
        }
        try {
            int index = FIRST_SLOT;
            for (Object handler : stackHandlers(player)) {
                int slots = (int) handler.getClass().getMethod("getSlots").invoke(handler);
                Method set = handler.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
                for (int i = 0; i < slots; i++, index++) {
                    set.invoke(handler, i, items.getOrDefault(index, ItemStack.EMPTY).copy());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            InventoryBackups.LOGGER.error("Could not restore Curios slots of {}", player.getScoreboardName(), e);
        }
    }

    /** CuriosApi.getCuriosInventory(player).getCurios().values().map(ICurioStacksHandler::getStacks). */
    private static Iterable<Object> stackHandlers(Player player) throws ReflectiveOperationException {
        Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
        Optional<?> inventory = (Optional<?>) api.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, player);
        if (inventory.isEmpty()) {
            return List.of();
        }
        Object curios = inventory.get();
        Map<?, ?> byType = (Map<?, ?>) curios.getClass().getMethod("getCurios").invoke(curios);
        List<Object> handlers = new ArrayList<>();
        for (Object stacksHandler : byType.values()) {
            handlers.add(stacksHandler.getClass().getMethod("getStacks").invoke(stacksHandler));
        }
        return handlers;
    }
}
