package com.alvaro.baixashopee.data.model;

public enum SortOrder {
    MANUAL,
    NAME_AZ,
    NEIGHBORHOOD;

    public static SortOrder fromString(String value) {
        if (value == null) return MANUAL;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MANUAL;
        }
    }
}
