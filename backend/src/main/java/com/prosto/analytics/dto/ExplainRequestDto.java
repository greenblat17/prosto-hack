package com.prosto.analytics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ExplainRequestDto(
        @NotNull @Valid PivotConfigDto config,
        @NotNull @Valid PivotResultDto result
) {
}
