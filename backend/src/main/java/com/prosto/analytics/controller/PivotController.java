package com.prosto.analytics.controller;

import com.prosto.analytics.dto.ExternalPivotRequestDto;
import com.prosto.analytics.dto.PivotExecuteRequestDto;
import com.prosto.analytics.dto.PivotResultDto;
import com.prosto.analytics.service.ConnectionService;
import com.prosto.analytics.service.PivotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Pivot", description = "Выполнение сводных таблиц и SQL-превью")
@RestController
@RequestMapping("/api/pivot")
public class PivotController {

    private final PivotService pivotService;
    private final ConnectionService connectionService;

    public PivotController(PivotService pivotService, ConnectionService connectionService) {
        this.pivotService = pivotService;
        this.connectionService = connectionService;
    }

    @Operation(summary = "Выполнить pivot-запрос",
            description = "Принимает конфигурацию сводной таблицы, выполняет SQL-агрегацию в PostgreSQL и возвращает результат")
    @PostMapping("/execute")
    public PivotResultDto execute(@RequestBody @Valid PivotExecuteRequestDto request) {
        return pivotService.execute(request);
    }

    @Operation(summary = "SQL-превью", description = "Возвращает читаемый SQL-запрос для текущей конфигурации")
    @PostMapping("/sql")
    public Map<String, String> previewSql(@RequestBody @Valid PivotExecuteRequestDto request) {
        return Map.of("sql", pivotService.previewSql(request));
    }

    @Operation(summary = "Pivot по внешней базе данных")
    @PostMapping("/external/execute")
    public PivotResultDto executeExternal(@RequestBody @Valid ExternalPivotRequestDto request, Authentication auth) {
        return pivotService.executeExternal(request, connectionService, auth.getName());
    }

    @Operation(summary = "SQL-превью для внешней базы данных")
    @PostMapping("/external/sql")
    public Map<String, String> previewExternalSql(@RequestBody @Valid ExternalPivotRequestDto request, Authentication auth) {
        return Map.of("sql", pivotService.previewExternalSql(request, connectionService, auth.getName()));
    }
}
