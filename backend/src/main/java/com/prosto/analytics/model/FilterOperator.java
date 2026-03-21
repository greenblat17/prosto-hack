package com.prosto.analytics.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FilterOperator {
    EQ("eq"),
    NEQ("neq"),
    GT("gt"),
    GTE("gte"),
    LT("lt"),
    LTE("lte"),
    IN("in");

    private final String value;

    FilterOperator(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static FilterOperator fromValue(String value) {
        for (FilterOperator op : values()) {
            if (op.value.equalsIgnoreCase(value)) return op;
        }
        throw new IllegalArgumentException("Unknown FilterOperator: " + value);
    }
}
