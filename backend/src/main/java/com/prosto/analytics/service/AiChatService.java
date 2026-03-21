package com.prosto.analytics.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.prosto.analytics.dto.*;
import com.prosto.analytics.model.ChatMessage;
import com.prosto.analytics.model.ChatSession;
import com.prosto.analytics.model.User;
import com.prosto.analytics.repository.ChatMessageRepository;
import com.prosto.analytics.repository.ChatSessionRepository;
import com.prosto.analytics.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private static final String SYSTEM_PROMPT = """
            Ты — ассистент бизнес-аналитика. Помогаешь строить сводные таблицы (pivot tables).

            Пользователь работает с датасетом. Тебе даны доступные поля.
            На основе запроса пользователя сгенерируй конфигурацию сводной таблицы.

            ОБЯЗАТЕЛЬНЫЕ ПРАВИЛА:
            - fieldId должен ТОЧНО совпадать с id поля из списка доступных полей
            - name должен совпадать с name поля
            - Используй ТОЛЬКО поля из предоставленного списка
            - rows — поля для строк (обычно текстовые/категориальные)
            - columns — поля для столбцов (обычно временные или категориальные)
            - values — поля для значений с агрегацией (sum, avg, count, count_distinct, list_distinct, min, max, median, variance, stddev, int_sum, first, last, running_sum, sum_pct_total, sum_pct_row, sum_pct_col, count_pct_total, count_pct_row, count_pct_col)
            - filters — фильтры (operator: eq, neq, gt, lt, in)
            - Числовые поля подходят для values, строковые — для rows/columns
            - ВСЕГДА добавляй хотя бы одно поле в values. Если нет подходящего числового — используй любое строковое с aggregation "count"
            - ВСЕГДА добавляй хотя бы одно поле в rows
            - Если пользователь задаёт фильтр, ВСЕГДА указывай filterValue с конкретным значением из запроса. НИКОГДА не возвращай filterValue: null
            - filterValue должен быть строкой (для eq/neq/gt/lt) или массивом строк (для in)

            Отвечай СТРОГО в формате JSON:
            {
              "text": "краткое описание что показывает таблица",
              "config": {
                "rows": [{"fieldId": "...", "name": "..."}],
                "columns": [],
                "values": [{"fieldId": "...", "name": "...", "aggregation": "sum"}],
                "filters": [{"fieldId": "...", "name": "...", "operator": "gt", "filterValue": "100"}]
              }
            }

            Если пользователь не задал конкретный запрос для таблицы, всё равно верни JSON с базовой конфигурацией используя первые подходящие поля.

            Отвечай только JSON, без markdown-обёртки.
            """;

    @Nullable
    private final GigaChatClient gigaChatClient;
    private final DatasetService datasetService;
    private final JsonMapper objectMapper;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;

    public AiChatService(@Nullable GigaChatClient gigaChatClient,
                         DatasetService datasetService,
                         JsonMapper objectMapper,
                         ChatSessionRepository sessionRepository,
                         ChatMessageRepository messageRepository,
                         UserRepository userRepository) {
        this.gigaChatClient = gigaChatClient;
        this.datasetService = datasetService;
        this.objectMapper = objectMapper;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;

        if (gigaChatClient == null) {
            log.warn("AI chat disabled: AI_API_KEY not configured. Chat endpoints will return fallback responses.");
        }
    }

    @Transactional
    public ChatResponseDto processMessage(String message, UUID datasetId, @Nullable UUID sessionId, String userEmail) {
        ChatSession session = null;
        if (sessionId != null) {
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new NoSuchElementException("User not found"));
            session = sessionRepository.findByIdAndUserId(sessionId, user.getId())
                    .orElseThrow(() -> new NoSuchElementException("Session not found"));
            if (!session.getDataset().getId().equals(datasetId)) {
                throw new IllegalArgumentException("Session does not belong to this dataset");
            }
        }

        // Save user message
        if (session != null) {
            ChatMessage userMsg = new ChatMessage();
            userMsg.setSession(session);
            userMsg.setRole("user");
            userMsg.setText(message);
            messageRepository.save(userMsg);

            // Auto-title from first message
            if (session.getTitle().equals("Новый чат")) {
                session.setTitle(message.length() > 50 ? message.substring(0, 50) + "..." : message);
            }
        }

        ChatResponseDto result;
        if (gigaChatClient == null) {
            result = fallbackResponse(message, datasetId);
        } else {
            result = doProcessMessage(message, datasetId, session);
        }

        // Save assistant response
        if (session != null) {
            ChatMessage assistantMsg = new ChatMessage();
            assistantMsg.setSession(session);
            assistantMsg.setRole("assistant");
            assistantMsg.setText(result.text() != null ? result.text() : "");
            if (result.config() != null) {
                try {
                    assistantMsg.setAppliedConfig(objectMapper.writeValueAsString(result.config()));
                } catch (JacksonException ignored) {}
            }
            messageRepository.save(assistantMsg);
            session.setUpdatedAt(java.time.LocalDateTime.now());
            sessionRepository.save(session);
        }

        return result;
    }

    public ChatResponseDto processExternalMessage(String message, List<DatasetFieldDto> fields) {
        if (gigaChatClient == null) {
            return fallbackResponseFromFields(message, fields);
        }
        return doProcessMessageWithFields(message, fields, null);
    }

    private ChatResponseDto doProcessMessage(String message, UUID datasetId, @Nullable ChatSession session) {
        List<DatasetFieldDto> fields = datasetService.getFields(datasetId);
        return doProcessMessageWithFields(message, fields, session);
    }

    private ChatResponseDto doProcessMessageWithFields(String message, List<DatasetFieldDto> fields, @Nullable ChatSession session) {
        String fieldsDescription = fields.stream()
                .map(f -> "  {id: \"" + f.id() + "\", name: \"" + f.name() +
                        "\", type: \"" + f.type().getValue() + "\", category: \"" + f.category() + "\"}")
                .collect(Collectors.joining("\n"));

        List<GigaChatClient.Message> history = new ArrayList<>();
        if (session != null) {
            var recentMessages = messageRepository.findTop20BySessionIdOrderByCreatedAtDesc(session.getId());
            for (int i = recentMessages.size() - 1; i >= 0; i--) {
                var m = recentMessages.get(i);
                if (i == 0 && m.getRole().equals("user") && m.getText().equals(message)) continue;
                history.add(new GigaChatClient.Message(m.getRole(), m.getText()));
            }
        }

        String userPrompt = "Доступные поля датасета:\n" + fieldsDescription +
                "\n\nЗапрос пользователя: " + message;

        try {
            String response = gigaChatClient.chatWithHistory(SYSTEM_PROMPT, history, userPrompt);

            var parsed = parseResponse(response);
            var sanitized = sanitizeResponse(parsed, fields);
            if (sanitized.config() == null) {
                var fallback = fallbackResponseFromFields(message, fields);
                return new ChatResponseDto(
                        parsed.text() != null ? parsed.text() : fallback.text(),
                        fallback.config()
                );
            }
            return sanitized;
        } catch (Exception e) {
            log.error("AI chat error", e);
            return fallbackResponseFromFields(message, fields);
        }
    }

    public String explainTable(ExplainRequestDto request) {
        if (gigaChatClient == null) {
            return fallbackExplain(request);
        }

        String configJson;
        String resultJson;
        try {
            configJson = objectMapper.writeValueAsString(request.config());
            var shortRows = request.result().rows().size() > 20
                    ? request.result().rows().subList(0, 20)
                    : request.result().rows();
            var shortResult = new PivotResultDto(
                    request.result().columnKeys(),
                    shortRows,
                    request.result().totals(),
                    request.result().totalRows(),
                    request.result().offset(),
                    request.result().limit()
            );
            resultJson = objectMapper.writeValueAsString(shortResult);
        } catch (JacksonException e) {
            return "Ошибка сериализации данных";
        }

        String prompt = """
                Проанализируй результаты сводной таблицы и дай краткие бизнес-выводы на русском языке.
                
                Конфигурация таблицы:
                %s
                
                Результаты (первые строки):
                %s
                
                Дай 3-5 ключевых инсайтов в виде маркированного списка. Укажи лидеров, аутсайдеров,
                значимые различия и тренды. Используй конкретные цифры.
                """.formatted(configJson, resultJson);

        try {
            return gigaChatClient.chat("", prompt);
        } catch (Exception e) {
            log.error("AI explain error", e);
            return "Не удалось сгенерировать аналитику: " + e.getMessage();
        }
    }

    private ChatResponseDto sanitizeResponse(ChatResponseDto response, List<DatasetFieldDto> fields) {
        if (response.config() == null) return response;

        var config = response.config();

        // Fix null filterValues
        var filters = config.filters() == null ? List.<PivotFilterFieldDto>of() :
                config.filters().stream().map(f -> new PivotFilterFieldDto(
                        f.fieldId(), f.name(),
                        f.operator() != null ? f.operator() : com.prosto.analytics.model.FilterOperator.EQ,
                        f.filterValue() != null ? f.filterValue() : List.of()
                )).toList();

        var rows = config.rows() == null ? List.<PivotFieldDto>of() : config.rows();
        var columns = config.columns() == null ? List.<PivotFieldDto>of() : config.columns();
        var values = config.values() == null ? List.<PivotValueFieldDto>of() : config.values();

        // Ensure at least one value field
        if (values.isEmpty()) {
            var numericField = fields.stream()
                    .filter(f -> f.type() == com.prosto.analytics.model.FieldType.NUMBER)
                    .findFirst();
            if (numericField.isPresent()) {
                values = List.of(new PivotValueFieldDto(numericField.get().id(),
                        numericField.get().name(), com.prosto.analytics.model.AggregationType.SUM));
            } else if (!fields.isEmpty()) {
                values = List.of(new PivotValueFieldDto(fields.getFirst().id(),
                        fields.getFirst().name(), com.prosto.analytics.model.AggregationType.COUNT));
            }
        }

        // Ensure at least one row field
        if (rows.isEmpty() && columns.isEmpty()) {
            var stringField = fields.stream()
                    .filter(f -> f.type() == com.prosto.analytics.model.FieldType.STRING)
                    .findFirst();
            if (stringField.isPresent()) {
                rows = List.of(new PivotFieldDto(stringField.get().id(), stringField.get().name()));
            }
        }

        var sanitizedConfig = new PivotConfigDto(rows, columns, values, filters);
        return new ChatResponseDto(response.text(), sanitizedConfig);
    }

    private ChatResponseDto fallbackResponse(String message, UUID datasetId) {
        List<DatasetFieldDto> fields = datasetService.getFields(datasetId);
        return fallbackResponseFromFields(message, fields);
    }

    private ChatResponseDto fallbackResponseFromFields(String message, List<DatasetFieldDto> fields) {
        var numericFields = fields.stream()
                .filter(f -> f.type() == com.prosto.analytics.model.FieldType.NUMBER)
                .toList();
        var stringFields = fields.stream()
                .filter(f -> f.type() == com.prosto.analytics.model.FieldType.STRING)
                .toList();

        var rows = stringFields.isEmpty()
                ? List.<PivotFieldDto>of()
                : List.of(new PivotFieldDto(stringFields.getFirst().id(), stringFields.getFirst().name()));

        var values = numericFields.isEmpty()
                ? List.<PivotValueFieldDto>of()
                : List.of(new PivotValueFieldDto(numericFields.getFirst().id(),
                        numericFields.getFirst().name(),
                        com.prosto.analytics.model.AggregationType.SUM));

        var config = new PivotConfigDto(rows, List.of(), values, List.of());

        return new ChatResponseDto(
                "AI-ассистент не настроен (нет AI_API_KEY). Показываю базовую сводку по первым доступным полям.",
                config
        );
    }

    private String fallbackExplain(ExplainRequestDto request) {
        int rowCount = request.result().rows().size();
        int totalCount = request.result().totals() != null ? request.result().totals().size() : 0;
        return "AI-ассистент не настроен. Таблица содержит %d строк и %d метрик.".formatted(rowCount, totalCount);
    }

    private ChatResponseDto parseResponse(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```json?\\s*", "").replaceAll("```$", "").trim();
        }

        // Try to fix truncated JSON by adding missing closing braces
        String fixed = fixTruncatedJson(cleaned);

        // Try direct parse
        ChatResponseDto result = tryParse(fixed);
        if (result != null && result.config() != null) return result;

        // Try to find JSON object in response text
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String extracted = fixTruncatedJson(cleaned.substring(start, end + 1));
            result = tryParse(extracted);
            if (result != null && result.config() != null) return result;
        }

        log.warn("Failed to parse AI response: {}", response);
        return new ChatResponseDto(cleaned, null);
    }

    private ChatResponseDto tryParse(String json) {
        try {
            return objectMapper.readValue(json, ChatResponseDto.class);
        } catch (JacksonException ignored) {
            return null;
        }
    }

    private String fixTruncatedJson(String json) {
        // Count open/close braces and brackets
        int braces = 0, brackets = 0;
        boolean inString = false;
        char prev = 0;
        for (char c : json.toCharArray()) {
            if (c == '"' && prev != '\\') inString = !inString;
            if (!inString) {
                if (c == '{') braces++;
                else if (c == '}') braces--;
                else if (c == '[') brackets++;
                else if (c == ']') brackets--;
            }
            prev = c;
        }
        StringBuilder sb = new StringBuilder(json);
        while (brackets > 0) { sb.append(']'); brackets--; }
        while (braces > 0) { sb.append('}'); braces--; }
        return sb.toString();
    }
}
