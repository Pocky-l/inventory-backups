package com.pockyl.inventorybackups.command;

import com.pockyl.inventorybackups.storage.Entry;
import com.pockyl.inventorybackups.storage.Trigger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.OptionalLong;

/**
 * How commands name an entry and filter lists. An entry is named by its time: the epoch milliseconds the chat buttons
 * use, {@code latest}, or a {@code yyyy-MM-dd-HH-mm-ss} timestamp in server time as in 2.x backup names (an optional
 * suffix such as {@code -death} is ignored). A name selects the newest entry at or before that time.
 */
public final class EntrySelector {
    public static final String LATEST = "latest";

    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int NAME_LENGTH = 19;
    /** Longest digit string that always fits a long. */
    private static final int MAX_DIGITS = 18;

    private EntrySelector() {
    }

    /** The time a name stands for, empty when it is not a valid name. */
    public static OptionalLong parse(String name) {
        if (name.equalsIgnoreCase(LATEST)) {
            return OptionalLong.of(Long.MAX_VALUE);
        }
        if (!name.isEmpty() && name.length() <= MAX_DIGITS && name.chars().allMatch(Character::isDigit)) {
            return OptionalLong.of(Long.parseLong(name));
        }
        if (name.length() >= NAME_LENGTH) {
            try {
                LocalDateTime time = LocalDateTime.parse(name.substring(0, NAME_LENGTH), NAME);
                // A 2.x name has second precision: select the newest entry within that second.
                return OptionalLong.of(time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 999);
            } catch (DateTimeParseException e) {
                return OptionalLong.empty();
            }
        }
        return OptionalLong.empty();
    }

    /** Readable server-time timestamp. */
    public static String display(long time) {
        return DISPLAY.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
    }

    /** The {@code yyyy-MM-dd-HH-mm-ss} name of a time, also used for date prefix filters. */
    public static String name(long time) {
        return NAME.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
    }

    /**
     * Whether an entry passes a list filter: empty, a trigger ({@code death}, {@code restore}, ...), a 2.x type
     * ({@code open}, {@code auto}) or a date prefix such as {@code 2025-03} or {@code 2025-03-15}.
     */
    public static boolean matches(Entry entry, String filter) {
        if (filter.isEmpty()) {
            return true;
        }
        Trigger trigger = Trigger.byKey(filter);
        if (trigger == null) {
            trigger = switch (filter) {
                case "open" -> Trigger.CONTAINER;
                case "auto" -> Trigger.CHANGE;
                default -> null;
            };
        }
        if (trigger != null) {
            return entry.trigger() == trigger;
        }
        return name(entry.time()).startsWith(filter);
    }
}
