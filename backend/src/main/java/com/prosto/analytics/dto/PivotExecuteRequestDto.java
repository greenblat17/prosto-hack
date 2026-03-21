package com.prosto.analytics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PivotExecuteRequestDto(
        @NotNull UUID datasetId,
        @NotNull @Valid PivotConfigDto config,
        Integer offset,
        Integer limit
) {
}
