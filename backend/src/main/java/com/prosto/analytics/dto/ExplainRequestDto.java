package com.prosto.analytics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ExplainRequestDto(
        @NotNull @Valid PivotConfigDto config,
        UUID datasetId,
        String connectionId,
        String schema,
        String tableName
) {
}
