package com.prosto.analytics.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AggregationType {
    ORIGINAL("original"),
    COUNT("count"),
    COUNT_DISTINCT("count_distinct"),
    LIST_DISTINCT("list_distinct"),
    SUM("sum"),
    INT_SUM("int_sum"),
    AVG("avg"),
    MEDIAN("median"),
    VARIANCE("variance"),
    STDDEV("stddev"),
    MIN("min"),
    MAX("max"),
    FIRST("first"),
    LAST("last"),
    RUNNING_SUM("running_sum"),
    SUM_PCT_TOTAL("sum_pct_total"),
    SUM_PCT_ROW("sum_pct_row"),
    SUM_PCT_COL("sum_pct_col"),
    COUNT_PCT_TOTAL("count_pct_total"),
    COUNT_PCT_ROW("count_pct_row"),
    COUNT_PCT_COL("count_pct_col");

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

    public String toSqlExpression(String column) {
        return toSqlExpression(column, false);
    }

    public String toSqlExpression(String column, boolean duckdb) {
        return switch (this) {
            case ORIGINAL -> column;
            case COUNT -> "COUNT(" + column + ")";
            case COUNT_DISTINCT -> "COUNT(DISTINCT " + column + ")";
            case LIST_DISTINCT -> duckdb
                    ? "STRING_AGG(DISTINCT CAST(" + column + " AS VARCHAR), ', ')"
                    : "STRING_AGG(DISTINCT " + column + "::text, ', ' ORDER BY " + column + "::text)";
            case SUM -> "SUM(" + column + ")";
            case INT_SUM -> duckdb
                    ? "CAST(SUM(" + column + ") AS BIGINT)"
                    : "SUM(" + column + ")::BIGINT";
            case AVG -> "AVG(" + column + ")";
            case MEDIAN -> duckdb
                    ? "MEDIAN(" + column + ")"
                    : "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY " + column + ")";
            case VARIANCE -> "VAR_SAMP(" + column + ")";
            case STDDEV -> "STDDEV_SAMP(" + column + ")";
            case MIN -> "MIN(" + column + ")";
            case MAX -> "MAX(" + column + ")";
            case FIRST -> duckdb
                    ? "FIRST(" + column + ")"
                    : "(ARRAY_AGG(" + column + "))[1]";
            case LAST -> duckdb
                    ? "LAST(" + column + ")"
                    : "(ARRAY_AGG(" + column + " ORDER BY " + column + " DESC))[1]";
            case RUNNING_SUM, SUM_PCT_TOTAL, SUM_PCT_ROW, SUM_PCT_COL -> "SUM(" + column + ")";
            case COUNT_PCT_TOTAL, COUNT_PCT_ROW, COUNT_PCT_COL -> "COUNT(" + column + ")";
        };
    }

    public String baseSqlExpression(String column) {
        return baseSqlExpression(column, false);
    }

    public String baseSqlExpression(String column, boolean duckdb) {
        return switch (this) {
            case RUNNING_SUM, SUM_PCT_TOTAL, SUM_PCT_ROW, SUM_PCT_COL -> "SUM(" + column + ")";
            case COUNT_PCT_TOTAL, COUNT_PCT_ROW, COUNT_PCT_COL -> "COUNT(" + column + ")";
            default -> toSqlExpression(column, duckdb);
        };
    }

    public boolean isWindowFunction() {
        return switch (this) {
            case RUNNING_SUM, SUM_PCT_TOTAL, SUM_PCT_ROW, SUM_PCT_COL,
                 COUNT_PCT_TOTAL, COUNT_PCT_ROW, COUNT_PCT_COL -> true;
            default -> false;
        };
    }

    public boolean requiresNumericColumn() {
        return switch (this) {
            case SUM, INT_SUM, AVG, MEDIAN, VARIANCE, STDDEV,
                 RUNNING_SUM, SUM_PCT_TOTAL, SUM_PCT_ROW, SUM_PCT_COL -> true;
            default -> false;
        };
    }

    public boolean returnsText() {
        return this == LIST_DISTINCT;
    }

    public String getDisplayLabel() {
        return switch (this) {
            case ORIGINAL -> "Оригинал";
            case COUNT -> "Количество";
            case COUNT_DISTINCT -> "Кол-во уникальных";
            case LIST_DISTINCT -> "Список уникальных";
            case SUM -> "Сумма";
            case INT_SUM -> "Целочисл. сумма";
            case AVG -> "Среднее";
            case MEDIAN -> "Медиана";
            case VARIANCE -> "Дисперсия";
            case STDDEV -> "Ст. отклонение";
            case MIN -> "Минимум";
            case MAX -> "Максимум";
            case FIRST -> "Первое";
            case LAST -> "Последнее";
            case RUNNING_SUM -> "Нарастающий итог";
            case SUM_PCT_TOTAL -> "% от итога (сумма)";
            case SUM_PCT_ROW -> "% от строк (сумма)";
            case SUM_PCT_COL -> "% от колонок (сумма)";
            case COUNT_PCT_TOTAL -> "% от итога (кол-во)";
            case COUNT_PCT_ROW -> "% от строк (кол-во)";
            case COUNT_PCT_COL -> "% от колонок (кол-во)";
        };
    }
}
