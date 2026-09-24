package com.rideflow.utility;

import java.util.Locale;

/** Canonical forms for identifiers that must compare equal regardless of how users type them. */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String email(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** "ts 09 ab-1234" becomes "TS09AB1234". */
    public static String identifier(String value) {
        return value.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
