package com.prosto.analytics.dto;

import java.util.List;

public record ColumnStatsDto(
        long totalRows,
        long distinctCount,
        long nullCount,
        String minValue,
        String maxValue,
        List<TopValue> topValues
) {
    public record TopValue(String value, long count) {}
}
