package com.prosto.analytics.controller;

import com.prosto.analytics.dto.*;
import com.prosto.analytics.service.ConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "External Connections", description = "Подключение к внешним PostgreSQL базам данных")
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @Operation(summary = "Проверить соединение", description = "Тестовое подключение без создания пула")
    @PostMapping("/test")
    public ConnectionTestResultDto testConnection(@RequestBody @Valid ConnectionRequestDto request) {
        return connectionService.testConnection(request);
    }

    @Operation(summary = "Подключиться к базе данных", description = "Создаёт пул соединений и возвращает connectionId")
    @PostMapping
    public ConnectionResponseDto connect(@RequestBody @Valid ConnectionRequestDto request, Authentication auth) {
        return connectionService.connect(request, auth.getName());
    }

    @Operation(summary = "Отключиться от базы данных", description = "Закрывает пул соединений")
    @DeleteMapping("/{connectionId}")
    public ResponseEntity<Void> disconnect(@PathVariable String connectionId, Authentication auth) {
        connectionService.disconnect(connectionId, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Список схем", description = "Возвращает схемы внешней базы данных")
    @GetMapping("/{connectionId}/schemas")
    public List<SchemaInfoDto> getSchemas(@PathVariable String connectionId, Authentication auth) {
        return connectionService.getSchemas(connectionId, auth.getName());
    }

    @Operation(summary = "Список таблиц в схеме")
    @GetMapping("/{connectionId}/schemas/{schema}/tables")
    public List<TableInfoDto> getTables(@PathVariable String connectionId, @PathVariable String schema,
                                         Authentication auth) {
        return connectionService.getTables(connectionId, schema, auth.getName());
    }

    @Operation(summary = "Поля таблицы", description = "Возвращает колонки с типами данных")
    @GetMapping("/{connectionId}/schemas/{schema}/tables/{table}/fields")
    public List<TableFieldDto> getTableFields(@PathVariable String connectionId,
                                               @PathVariable String schema,
                                               @PathVariable String table,
                                               Authentication auth) {
        return connectionService.getTableFields(connectionId, schema, table, auth.getName());
    }
}
