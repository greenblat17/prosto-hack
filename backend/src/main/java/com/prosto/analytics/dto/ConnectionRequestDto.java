package com.prosto.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConnectionRequestDto(
        @NotBlank String host,
        @NotNull Integer port,
        @NotBlank String database,
        @NotBlank String username,
        @NotBlank String password,
        String name
) {
}
