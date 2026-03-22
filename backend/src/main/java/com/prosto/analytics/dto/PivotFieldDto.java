package com.prosto.analytics.dto;

import jakarta.validation.constraints.NotBlank;

public record PivotFieldDto(
        @NotBlank String fieldId,
        @NotBlank String name
) {
}
