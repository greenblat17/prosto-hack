package com.prosto.analytics.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DatasetInfoDto(
        UUID id,
        String name,
        long rowCount,
        int columnCount,
        LocalDateTime createdAt
) {
}
