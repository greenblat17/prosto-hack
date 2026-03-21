package com.prosto.analytics.dto;

import java.util.List;
import java.util.Map;

public record PivotResultRowDto(
        List<String> keys,
        Map<String, Object> values,
        List<PivotResultRowDto> children
) {
}
