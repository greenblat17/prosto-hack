package com.prosto.analytics.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageDto(
        UUID id,
        String role,
        String text,
        String appliedConfig,
        LocalDateTime createdAt
) {}
