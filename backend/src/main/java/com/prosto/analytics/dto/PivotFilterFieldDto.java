package com.prosto.analytics.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import com.prosto.analytics.dto.json.FilterValueDeserializer;
import com.prosto.analytics.model.FilterOperator;

import java.util.List;

public record PivotFilterFieldDto(
        String fieldId,
        String name,
        FilterOperator operator,
        @JsonDeserialize(using = FilterValueDeserializer.class)
        List<String> filterValue
) {
}
