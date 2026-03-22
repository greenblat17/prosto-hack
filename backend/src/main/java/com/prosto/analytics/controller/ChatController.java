package com.prosto.analytics.controller;

import com.prosto.analytics.dto.*;
import com.prosto.analytics.model.FieldType;
import com.prosto.analytics.service.AiChatService;
import com.prosto.analytics.service.ConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI Chat", description = "AI-ассистент для генерации pivot-конфигураций и аналитики")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AiChatService aiChatService;
    private final ConnectionService connectionService;

    public ChatController(AiChatService aiChatService, ConnectionService connectionService) {
        this.aiChatService = aiChatService;
        this.connectionService = connectionService;
    }

    @Operation(summary = "Отправить сообщение AI-ассистенту",
            description = "AI анализирует запрос и генерирует конфигурацию сводной таблицы на основе доступных полей датасета")
    @PostMapping("/message")
    public ChatResponseDto sendMessage(@RequestBody @Valid ChatRequestDto request, Authentication auth) {
        return aiChatService.processMessage(request.message(), request.datasetId(), request.sessionId(), auth.getName());
    }

    @Operation(summary = "AI-ассистент для внешней таблицы",
            description = "AI генерирует конфигурацию на основе полей внешней таблицы")
    @PostMapping("/external/message")
    public ChatResponseDto sendExternalMessage(@RequestBody @Valid ExternalChatRequestDto request, Authentication auth) {
        String userEmail = auth.getName();
        List<TableFieldDto> tableFields = connectionService.getTableFields(
                request.connectionId(), request.schema(), request.tableName(), userEmail);

        List<DatasetFieldDto> fields = tableFields.stream()
                .map(f -> new DatasetFieldDto(f.name(), f.name(), mapPgType(f.type()), categorize(mapPgType(f.type()))))
                .toList();

        long rowCount = connectionService.getTableRowCount(
                request.connectionId(), request.schema(), request.tableName(), userEmail);

        return aiChatService.processExternalMessage(request.message(), fields, rowCount);
    }

    @Operation(summary = "Объяснить таблицу",
            description = "AI выполняет pivot-запрос на сервере, считает статистику и даёт бизнес-выводы. Возвращает plain text.")
    @PostMapping(value = "/explain", produces = "text/plain")
    public String explain(@RequestBody @Valid ExplainRequestDto request, Authentication auth) {
        return aiChatService.explainTable(request, connectionService, auth.getName());
    }

    private static FieldType mapPgType(String pgType) {
        if (pgType == null) return FieldType.STRING;
        String lower = pgType.toLowerCase();
        if (lower.contains("int") || lower.contains("numeric") || lower.contains("decimal")
                || lower.contains("float") || lower.contains("double") || lower.contains("real")
                || lower.contains("serial") || lower.contains("money")) {
            return FieldType.NUMBER;
        }
        if (lower.contains("date") || lower.contains("time") || lower.contains("interval")) {
            return FieldType.DATE;
        }
        if (lower.contains("bool")) {
            return FieldType.BOOLEAN;
        }
        return FieldType.STRING;
    }

    private static final String DIMENSION = "dimension";

    private static String categorize(FieldType type) {
        return switch (type) {
            case NUMBER -> "measure";
            case STRING, DATE, BOOLEAN -> DIMENSION;
        };
    }
}
