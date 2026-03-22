package com.prosto.analytics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PivotConfigDto(
        @NotNull List<@Valid PivotFieldDto> rows,
        @NotNull List<@Valid PivotFieldDto> columns,
        @NotNull List<@Valid PivotValueFieldDto> values,
        List<@Valid PivotFilterFieldDto> filters
) {
}
