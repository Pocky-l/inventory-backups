package com.pockyl.inventorybackups.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntComparator;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.backup.Slots;

import java.util.List;
import java.util.Locale;

/**
 * Readable names of the slots other mods add, from the slot keys the server sends with each state (see
 * {@link com.pockyl.inventorybackups.storage.SlotKeys}). Slot type names come from the slot mod's own translations
 * when the client has that mod, otherwise from the slot id.
 */
public final class ModSlotNames {
    private static final List<String> PROVIDER_ORDER = List.of("curios", "accessories", "cosmeticarmor", "travelersbackpack",
            "toolbelt", "backpacked");
    private static final String COSMETIC = "cosmetic/";
    private static final Int2ObjectMap<String> KEYS = new Int2ObjectOpenHashMap<>();

    /** Mod slots first by 2.x curio number, then by mod, slot type, plain before cosmetic, and index. */
    public static final IntComparator ORDER = (a, b) -> {
        boolean legacyA = Slots.isCurio(a);
        boolean legacyB = Slots.isCurio(b);
        if (legacyA || legacyB) {
            return legacyA && legacyB ? Integer.compare(a, b) : legacyA ? -1 : 1;
        }
        Parsed pa = parse(KEYS.get(a));
        Parsed pb = parse(KEYS.get(b));
        if (pa == null || pb == null) {
            return pa == pb ? Integer.compare(a, b) : pa == null ? 1 : -1;
        }
        int order = Integer.compare(pa.providerRank(), pb.providerRank());
        if (order == 0) {
            order = pa.provider().compareTo(pb.provider());
        }
        if (order == 0) {
            order = pa.type().compareTo(pb.type());
        }
        if (order == 0) {
            order = Boolean.compare(pa.cosmetic(), pb.cosmetic());
        }
        return order != 0 ? order : Integer.compare(pa.index(), pb.index());
    };

    private ModSlotNames() {
    }

    /** Remembers the keys sent with a state; the numbers are the same for the whole world. */
    public static void remember(Int2ObjectMap<String> keys) {
        KEYS.putAll(keys);
    }

    public static void forget() {
        KEYS.clear();
    }

    /** Name of a mod slot, for example "Curios · ring 2"; the number when the key is unknown. */
    public static Component name(int slot) {
        if (Slots.isCurio(slot)) {
            return Component.translatable("inventorybackups.ui.slot.curio", slot - Slots.CURIOS_START + 1);
        }
        Parsed parsed = parse(KEYS.get(slot));
        if (parsed == null) {
            return Component.translatable("inventorybackups.ui.slot.mod_unknown", slot);
        }
        Component mod = Component.translatableWithFallback("inventorybackups.ui.mod." + parsed.provider(), parsed.provider());
        return Component.translatable("inventorybackups.ui.slot.mod", mod, slotName(parsed));
    }

    /** The group a mod slot is shown in on the admin screen: its mod, or {@code legacy} for 2.x curios. */
    public static String group(int slot) {
        if (Slots.isCurio(slot)) {
            return "legacy";
        }
        Parsed parsed = parse(KEYS.get(slot));
        return parsed == null ? "unknown" : parsed.provider();
    }

    public static Component groupName(String group) {
        return switch (group) {
            case "legacy" -> Component.translatable("inventorybackups.ui.mod.legacy");
            case "unknown" -> Component.translatable("inventorybackups.ui.mod.unknown");
            default -> Component.translatableWithFallback("inventorybackups.ui.mod." + group, group);
        };
    }

    private static Component slotName(Parsed parsed) {
        int number = parsed.index() + 1;
        Component name = switch (parsed.provider()) {
            case "curios" -> Component.translatable("inventorybackups.ui.slot.indexed",
                    Component.translatableWithFallback("curios.identifier." + parsed.type(), pretty(parsed.type())), number);
            case "accessories" -> Component.translatable("inventorybackups.ui.slot.indexed",
                    Component.translatableWithFallback("accessories.slot." + parsed.type().replace(':', '.'), pretty(parsed.type())),
                    number);
            case "cosmeticarmor" -> parsed.index() < Slots.ARMOR_SIZE
                    ? Component.translatable("inventorybackups.ui.slot.armor." + parsed.index())
                    : Component.translatable("inventorybackups.ui.slot.indexed", pretty(parsed.type()), number);
            case "travelersbackpack" -> Component.translatable("inventorybackups.ui.slot.back");
            case "toolbelt" -> Component.translatable("inventorybackups.ui.slot.belt");
            case "backpacked" -> Component.translatable("inventorybackups.ui.slot.backpack", number);
            default -> parsed.index() < 0 ? Component.literal(pretty(parsed.type()))
                    : Component.translatable("inventorybackups.ui.slot.indexed", pretty(parsed.type()), number);
        };
        return parsed.cosmetic() ? Component.translatable("inventorybackups.ui.slot.cosmetic", name) : name;
    }

    /** {@code ring} from {@code ring}, {@code charm} from {@code artifacts:charm}, spaces for underscores. */
    private static String pretty(String type) {
        String name = type.substring(type.indexOf(':') + 1).replace('_', ' ');
        return name.isEmpty() ? type : name.toLowerCase(Locale.ROOT);
    }

    private record Parsed(String provider, int providerRank, String type, boolean cosmetic, int index) {
    }

    @Nullable
    private static Parsed parse(@Nullable String key) {
        if (key == null) {
            return null;
        }
        int colon = key.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String provider = key.substring(0, colon);
        String slot = key.substring(colon + 1);
        boolean cosmetic = slot.startsWith(COSMETIC);
        if (cosmetic) {
            slot = slot.substring(COSMETIC.length());
        }
        int rank = PROVIDER_ORDER.indexOf(provider);
        rank = rank < 0 ? PROVIDER_ORDER.size() : rank;
        int slash = slot.lastIndexOf('/');
        if (slash > 0) {
            try {
                return new Parsed(provider, rank, slot.substring(0, slash), cosmetic, Integer.parseInt(slot.substring(slash + 1)));
            } catch (NumberFormatException ignored) {
                // Not an indexed slot: the whole name is the type.
            }
        }
        return new Parsed(provider, rank, slot, cosmetic, -1);
    }
}
