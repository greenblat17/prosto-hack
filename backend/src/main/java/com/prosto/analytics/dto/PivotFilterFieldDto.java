package com.prosto.analytics.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import com.prosto.analytics.dto.json.FilterValueDeserializer;
import com.prosto.analytics.model.FilterOperator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PivotFilterFieldDto(
        @NotBlank String fieldId,
        @NotBlank String name,
        @NotNull FilterOperator operator,
        @JsonDeserialize(using = FilterValueDeserializer.class)
        List<String> filterValue
) {
}
