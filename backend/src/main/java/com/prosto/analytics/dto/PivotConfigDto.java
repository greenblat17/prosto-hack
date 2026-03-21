package com.prosto.analytics.dto;

import java.util.List;

public record PivotConfigDto(
        List<PivotFieldDto> rows,
        List<PivotFieldDto> columns,
        List<PivotValueFieldDto> values,
        List<PivotFilterFieldDto> filters
) {
}
