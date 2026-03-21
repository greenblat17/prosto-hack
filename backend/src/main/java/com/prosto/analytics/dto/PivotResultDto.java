package com.prosto.analytics.dto;

import java.util.List;
import java.util.Map;

public record PivotResultDto(
        List<List<String>> columnKeys,
        List<PivotResultRowDto> rows,
        Map<String, Double> totals,
        long totalRows,
        int offset,
        int limit
) {
}
