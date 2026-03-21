package com.prosto.analytics.dto;

public record ChatResponseDto(
        String text,
        PivotConfigDto config
) {
}
