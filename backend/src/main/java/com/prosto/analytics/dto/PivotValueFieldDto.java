package com.prosto.analytics.dto;

import com.prosto.analytics.model.AggregationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PivotValueFieldDto(
        @NotBlank String fieldId,
        @NotBlank String name,
        @NotNull AggregationType aggregation
) {
}
