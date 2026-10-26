package com.pockyl.inventorybackups.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.InventoryBackups;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The slots other mods add to players: Curios, Accessories, Cosmetic Armor Reworked, Traveler's Backpack, Tool Belt
 * and Backpacked. A slot is named by a key {@code <provider>:<slot>}, for example {@code curios:ring/1} or
 * {@code accessories:cosmetic/hat/0}. A provider that fails (an incompatible version of its mod) is switched off with
 * one log message instead of failing every scan.
 */
public final class ModSlots {
    private static final List<SlotProvider> PROVIDERS = new CopyOnWriteArrayList<>(List.of(new CuriosSlots(), new AccessoriesSlots(),
            new CosmeticArmorSlots(), new TravelersBackpackSlots(), new ToolBeltSlots(), new BackpackedSlots()));
    private static final Set<String> BROKEN = ConcurrentHashMap.newKeySet();
    @Nullable
    private static volatile List<SlotProvider> active;

    private ModSlots() {
    }

    /** Adds a provider; game tests use it for slots of their own. */
    public static void addProvider(SlotProvider provider) {
        PROVIDERS.add(provider);
        active = null;
    }

    public static void removeProvider(SlotProvider provider) {
        PROVIDERS.remove(provider);
        active = null;
    }

    /** Whether any mod with slots is installed. */
    public static boolean any() {
        return !active().isEmpty();
    }

    /** Ids of the providers of the installed mods, for the log. */
    public static List<String> installed() {
        return active().stream().map(SlotProvider::id).toList();
    }

    /** Every mod slot of the player, empty ones included, with the live stacks (not copies), in a stable order. */
    public static Map<String, ItemStack> read(Player player) {
        Map<String, ItemStack> slots = new LinkedHashMap<>();
        for (SlotProvider provider : active()) {
            String prefix = provider.id() + ':';
            try {
                provider.read(player, (slot, stack) -> slots.put(prefix + slot, stack));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                fail(provider, e);
            }
        }
        return slots;
    }

    /** The live stack in a slot; empty when the slot or its mod is missing. */
    public static ItemStack get(Player player, String key) {
        SlotProvider provider = provider(key);
        if (provider != null) {
            try {
                return provider.get(player, local(key));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                fail(provider, e);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Puts a stack into a slot.
     *
     * @return false when the slot or its mod is missing or the mod refuses the stack; nothing changed then
     */
    public static boolean set(Player player, String key, ItemStack stack) {
        SlotProvider provider = provider(key);
        if (provider != null) {
            try {
                return provider.set(player, local(key), stack);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                fail(provider, e);
            }
        }
        return false;
    }

    public static boolean accepts(Player player, String key, ItemStack stack) {
        SlotProvider provider = provider(key);
        return provider != null && provider.accepts(player, local(key), stack);
    }

    /** Keys of the Curios slots in the order Curios lists them, for the curio numbers of 2.x backups. */
    public static List<String> legacyCurioKeys(Player player) {
        List<String> keys = new ArrayList<>();
        for (SlotProvider provider : active()) {
            if (provider instanceof CuriosSlots curios) {
                try {
                    curios.legacyOrder(player).forEach(slot -> keys.add(curios.id() + ':' + slot));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    fail(provider, e);
                }
            }
        }
        return keys;
    }

    /** The provider id of a key ({@code curios} for {@code curios:ring/0}). */
    public static String providerOf(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? key : key.substring(0, colon);
    }

    private static String local(String key) {
        return key.substring(key.indexOf(':') + 1);
    }

    @Nullable
    private static SlotProvider provider(String key) {
        String id = providerOf(key);
        for (SlotProvider provider : active()) {
            if (provider.id().equals(id)) {
                return provider;
            }
        }
        return null;
    }

    private static List<SlotProvider> active() {
        List<SlotProvider> list = active;
        if (list == null) {
            list = new ArrayList<>();
            for (SlotProvider provider : PROVIDERS) {
                if (!BROKEN.contains(provider.id()) && available(provider)) {
                    list.add(provider);
                }
            }
            list = List.copyOf(list);
            active = list;
        }
        return list;
    }

    private static boolean available(SlotProvider provider) {
        try {
            return provider.available();
        } catch (RuntimeException | LinkageError e) {
            InventoryBackups.LOGGER.error("Could not check the {} slots", provider.id(), e);
            return false;
        }
    }

    private static void fail(SlotProvider provider, Throwable error) {
        if (BROKEN.add(provider.id())) {
            InventoryBackups.LOGGER.error("Could not access the {} slots; they are not backed up until the next start. "
                    + "Please report this with the version of that mod", provider.id(), error);
        }
        active = null;
    }
}
