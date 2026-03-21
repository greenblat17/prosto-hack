package com.prosto.analytics.dto;

import jakarta.validation.constraints.NotBlank;

public record ExternalChatRequestDto(
        @NotBlank String message,
        @NotBlank String connectionId,
        @NotBlank String schema,
        @NotBlank String tableName
) {
}
