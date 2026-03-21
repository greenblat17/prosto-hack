package com.prosto.analytics.dto;

import com.prosto.analytics.model.FieldType;

public record DatasetFieldDto(
        String id,
        String name,
        FieldType type,
        String category
) {
}
