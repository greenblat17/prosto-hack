package com.prosto.analytics.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatSessionDto(
        UUID id,
        UUID datasetId,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
