package com.lariflix.jemm.tagteam.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * How multiple {@link TagRequire} items are combined: any (OR) or all (AND).
 */
public enum RequireMode {
    ANY,
    ALL;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static RequireMode fromJson(String value) {
        if (value == null || value.isBlank()) {
            return ANY;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if ("all".equals(v) || "and".equals(v)) {
            return ALL;
        }
        return ANY;
    }
}
