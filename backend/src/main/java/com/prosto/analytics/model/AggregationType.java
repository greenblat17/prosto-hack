package com.prosto.analytics.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AggregationType {
    RAW("raw"),
    SUM("sum"),
    AVG("avg"),
    COUNT("count"),
    MIN("min"),
    MAX("max");

    private final String value;

    AggregationType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AggregationType fromValue(String value) {
        for (AggregationType at : values()) {
            if (at.value.equalsIgnoreCase(value)) return at;
        }
        throw new IllegalArgumentException("Unknown AggregationType: " + value);
    }

    public String toSql() {
        return this == RAW ? "MIN" : name();
    }
}
