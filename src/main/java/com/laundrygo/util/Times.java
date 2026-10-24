package com.laundrygo.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Timestamp parse/format helpers. SQLite stores ISO-8601 text. */
public final class Times {

    public static final DateTimeFormatter STAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.ENGLISH);
    public static final DateTimeFormatter SHORT =
            DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.ENGLISH);
    public static final DateTimeFormatter DUE =
            DateTimeFormatter.ofPattern("MMM d, EEE · h:mm a", Locale.ENGLISH);
    public static final DateTimeFormatter HEADER_DATE =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
    public static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private Times() {
    }

    public static String stamp(LocalDateTime value) {
        return value == null ? null : value.format(STAMP);
    }

    public static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value, STAMP);
    }

    public static String display(LocalDateTime value) {
        return value == null ? "—" : value.format(DISPLAY);
    }

    public static String shortDisplay(LocalDateTime value) {
        return value == null ? "—" : value.format(SHORT);
    }
}
