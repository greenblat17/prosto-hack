package com.prosto.analytics.dto;

public record TableInfoDto(
        String name,
        String schema,
        Long estimatedRows
) {
}
