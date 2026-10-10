package com.pockyl.inventorybackups.index;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What to search for: an item id ({@code minecraft:diamond} or {@code diamond}), a custom name ({@code name:Excalibur}),
 * an enchantment ({@code ench:sharpness}) or the fingerprint of one specific stack ({@code #1a2b3c}).
 */
public record SearchQuery(Kind kind, String value) {
    public enum Kind {
        ITEM, NAME, ENCHANTMENT, FINGERPRINT
    }

    /** Longest accepted query text; network requests are cut to this length. */
    public static final int MAX_LENGTH = 128;

    @Nullable
    public static SearchQuery parse(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("name:")) {
            String name = lower.substring(5).trim();
            return name.isEmpty() ? null : new SearchQuery(Kind.NAME, name);
        }
        if (lower.startsWith("ench:")) {
            ResourceLocation id = ResourceLocation.tryParse(lower.substring(5).trim());
            return id == null ? null : new SearchQuery(Kind.ENCHANTMENT, id.toString());
        }
        if (lower.startsWith("#")) {
            return fingerprint(lower.substring(1));
        }
        ResourceLocation id = ResourceLocation.tryParse(lower);
        return id == null ? null : new SearchQuery(Kind.ITEM, id.toString());
    }

    @Nullable
    public static SearchQuery fingerprint(String hex) {
        try {
            return new SearchQuery(Kind.FINGERPRINT, Long.toHexString(Long.parseUnsignedLong(hex.trim(), 16)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The query as it would be typed. */
    public String text() {
        return switch (kind) {
            case ITEM -> value;
            case NAME -> "name:" + value;
            case ENCHANTMENT -> "ench:" + value;
            case FINGERPRINT -> "#" + value;
        };
    }
}
