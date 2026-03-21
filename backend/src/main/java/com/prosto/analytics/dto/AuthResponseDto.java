package com.prosto.analytics.dto;

public record AuthResponseDto(
        String token,
        String email
) {
}
