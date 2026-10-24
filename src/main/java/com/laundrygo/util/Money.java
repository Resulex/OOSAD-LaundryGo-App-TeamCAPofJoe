package com.laundrygo.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;

/** Peso formatting. Always renders the ₱ glyph regardless of the JVM's default locale. */
public final class Money {

    private Money() {
    }

    public static String format(double value) {
        return "₱" + new DecimalFormat("#,##0.00").format(value);
    }

    public static String plain(double value) {
        return new DecimalFormat("#,##0.00").format(value);
    }

    /** Parses "1,250.50", "₱1250.50" or "1250" — returns null when the text is not a number. */
    public static Double parse(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replace("₱", "").replace(",", "").replace(" ", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(cleaned).doubleValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
