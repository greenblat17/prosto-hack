package com.prosto.analytics.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FieldType {
    STRING("string"),
    NUMBER("number"),
    DATE("date"),
    BOOLEAN("boolean");

    private final String value;

    FieldType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static FieldType fromValue(String value) {
        for (FieldType ft : values()) {
            if (ft.value.equalsIgnoreCase(value)) return ft;
        }
        throw new IllegalArgumentException("Unknown FieldType: " + value);
    }

    public String toSqlType() {
        return switch (this) {
            case NUMBER -> "NUMERIC";
            case DATE -> "DATE";
            case BOOLEAN -> "BOOLEAN";
            case STRING -> "TEXT";
        };
    }
}
