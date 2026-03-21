package com.prosto.analytics.service;

public enum SqlDialect {
    POSTGRESQL,
    DUCKDB;

    public String castText(String placeholder) {
        return switch (this) {
            case POSTGRESQL -> placeholder + "::text";
            case DUCKDB -> "CAST(" + placeholder + " AS VARCHAR)";
        };
    }

    public String castNumeric(String placeholder) {
        return switch (this) {
            case POSTGRESQL -> placeholder + "::numeric";
            case DUCKDB -> "CAST(" + placeholder + " AS DOUBLE)";
        };
    }
}
