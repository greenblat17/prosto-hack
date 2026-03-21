package com.prosto.analytics.dto;

import com.prosto.analytics.model.AggregationType;

public record PivotValueFieldDto(
        String fieldId,
        String name,
        AggregationType aggregation
) {
}
