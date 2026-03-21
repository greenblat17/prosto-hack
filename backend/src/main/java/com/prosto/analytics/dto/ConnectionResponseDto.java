package com.prosto.analytics.dto;

public record ConnectionResponseDto(
        String connectionId,
        String name,
        String host,
        int port,
        String database
) {
}
