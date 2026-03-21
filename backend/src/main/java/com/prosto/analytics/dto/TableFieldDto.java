package com.prosto.analytics.dto;

public record TableFieldDto(
        String name,
        String type,
        boolean nullable
) {
}
