package com.prosto.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChatRequestDto(
        @NotBlank String message,
        @NotNull UUID datasetId,
        UUID sessionId
) {
}
