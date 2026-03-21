package com.prosto.analytics.dto;

public record ConnectionTestResultDto(
        boolean success,
        String message
) {
}
