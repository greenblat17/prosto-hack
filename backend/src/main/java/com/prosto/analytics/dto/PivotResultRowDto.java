package com.prosto.analytics.dto;

import java.util.List;
import java.util.Map;

public record PivotResultRowDto(
        List<String> keys,
        Map<String, Double> values,
        List<PivotResultRowDto> children
) {
}
