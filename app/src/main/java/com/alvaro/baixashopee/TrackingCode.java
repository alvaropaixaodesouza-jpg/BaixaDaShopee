package com.alvaro.baixashopee;

import java.text.Normalizer;
import java.util.Locale;

public final class TrackingCode {
    private TrackingCode() {}

    public static String clean(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", "");
    }

    public static String onlyDigits(String value) {
        return clean(value).replaceAll("[^0-9]", "");
    }

    public static String stableId(String value) {
        return clean(value).toUpperCase(Locale.ROOT);
    }

    public static String normalizeHeader(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return normalized.replaceAll("[^a-z0-9]+", " ").trim();
    }

    public static boolean looksLikeTrackingCode(String value) {
        String code = clean(value);
        if (code.length() < 8 || code.length() > 40) return false;
        String digits = onlyDigits(code);
        if (digits.length() < 6) return false;
        return code.matches("[A-Za-z0-9._/-]+");
    }
}
