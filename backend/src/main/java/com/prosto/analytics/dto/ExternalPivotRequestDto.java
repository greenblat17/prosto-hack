package com.prosto.analytics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExternalPivotRequestDto(
        @NotBlank String connectionId,
        @NotBlank String schema,
        @NotBlank String tableName,
        @NotNull @Valid PivotConfigDto config,
        Integer offset,
        Integer limit
) {
}
