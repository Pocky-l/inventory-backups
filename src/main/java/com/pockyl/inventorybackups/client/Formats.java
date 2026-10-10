package com.pockyl.inventorybackups.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Times, durations and places as the admin screen shows them, in the admin's own time zone and language.
 */
public final class Formats {
    private Formats() {
    }

    private static Locale locale() {
        String code = Minecraft.getInstance().options.languageCode;
        return Locale.forLanguageTag(code.replace('_', '-'));
    }

    private static String clock() {
        return ClientConfig.CLOCK_24H.get() ? "HH:mm" : "h:mm a";
    }

    private static ZonedDateTime zoned(long time) {
        return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
    }

    /** Today: 14:02:11; this week: Mon 14:02; older: 2026-09-12 14:02. */
    public static String time(long time) {
        ZonedDateTime when = zoned(time);
        LocalDate today = LocalDate.now();
        String clock = clock();
        String seconds = ClientConfig.CLOCK_24H.get() ? "HH:mm:ss" : "h:mm:ss a";
        if (when.toLocalDate().equals(today)) {
            return DateTimeFormatter.ofPattern(seconds, locale()).format(when);
        }
        if (when.toLocalDate().isAfter(today.minusDays(6))) {
            return DateTimeFormatter.ofPattern("EEE " + clock, locale()).format(when);
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd " + clock, locale()).format(when);
    }

    /** Full date and time with seconds. */
    public static String full(long time) {
        String seconds = ClientConfig.CLOCK_24H.get() ? "HH:mm:ss" : "h:mm:ss a";
        return DateTimeFormatter.ofPattern("yyyy-MM-dd " + seconds, locale()).format(zoned(time));
    }

    /** Short tick label: a time of day, or a date for long ranges. */
    public static String tick(long time, long span) {
        String pattern = span <= 2 * 86_400_000L ? clock() : span <= 14 * 86_400_000L ? "EEE d" : "MMM d";
        return DateTimeFormatter.ofPattern(pattern, locale()).format(zoned(time));
    }

    public static String clockNow() {
        return DateTimeFormatter.ofPattern(clock(), locale()).format(ZonedDateTime.now());
    }

    /** "just now", "12 min ago", "3 h ago", "2 days ago". */
    public static Component ago(long time) {
        long minutes = Math.max(0, (System.currentTimeMillis() - time) / 60_000L);
        if (minutes < 1) {
            return Component.translatable("inventorybackups.ui.ago.now");
        }
        if (minutes < 60) {
            return Component.translatable("inventorybackups.ui.ago.minutes", minutes);
        }
        if (minutes < 48 * 60) {
            return Component.translatable("inventorybackups.ui.ago.hours", minutes / 60);
        }
        return Component.translatable("inventorybackups.ui.ago.days", minutes / (24 * 60));
    }

    /** "overworld 120 64 -30", without the minecraft namespace. */
    public static String place(String dimension, int x, int y, int z) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        String name = id != null && id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) ? id.getPath() : dimension;
        return name + " " + x + " " + y + " " + z;
    }
}
