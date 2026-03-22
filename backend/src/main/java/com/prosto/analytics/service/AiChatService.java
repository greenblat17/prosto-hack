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

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private static final String SYSTEM_PROMPT = """
            Ты — ассистент бизнес-аналитика. Помогаешь строить сводные таблицы (pivot tables).
            Пользователь работает с датасетом. Тебе даны доступные поля.
            На основе запроса пользователя сгенерируй конфигурацию сводной таблицы.

            СТРУКТУРА КОНФИГУРАЦИИ:
            - rows — поля для строк (категориальные/текстовые поля)
            - columns — поля для столбцов (временные/категориальные, не обязательно)
            - values — поля со значениями и агрегацией
            - filters — фильтры (operator: eq, neq, gt, gte, lt, lte, in)

            ДОСТУПНЫЕ АГРЕГАЦИИ (aggregation):
            Без группировки:
              "original" — показать сырые данные без группировки (каждая строка как есть). ЭТО ДЕФОЛТНАЯ АГРЕГАЦИЯ.

            Подсчёт:
              "count" — количество значений
              "count_distinct" — количество уникальных значений
              "list_distinct" — список уникальных значений (строка через запятую)

            Математические (только для числовых полей):
              "sum" — сумма
              "int_sum" — целочисленная сумма
              "avg" — среднее арифметическое
              "median" — медиана
              "variance" — выборочная дисперсия
              "stddev" — стандартное выборочное отклонение
              "min" — минимум
              "max" — максимум

            Позиционные:
              "first" — первое значение в группе
              "last" — последнее значение в группе
              "running_sum" — нарастающий итог (сумма за суммой)

            Доли от суммы (только для числовых полей):
              "sum_pct_total" — доля суммы от общего итога (%)
              "sum_pct_row" — доля суммы от строки (%)
              "sum_pct_col" — доля суммы от колонки (%)

            Доли от количества:
              "count_pct_total" — доля количества от общего итога (%)
              "count_pct_row" — доля количества от строки (%)
              "count_pct_col" — доля количества от колонки (%)

            ОБЯЗАТЕЛЬНЫЕ ПРАВИЛА:
            - fieldId и name ТОЧНО совпадают с полями из предоставленного списка
            - Используй ТОЛЬКО поля из списка
            - ВСЕГДА добавляй хотя бы одно поле в values и хотя бы одно поле в rows
            - Если пользователь просит показать данные без агрегации / просто данные / «покажи все» — используй aggregation "original"
            - Если пользователь просит сумму, среднее, количество и т.д. — используй соответствующую агрегацию
            - Если пользователь не уточняет агрегацию — используй "original" для числовых полей
            - Для фильтров: filterValue — строка (для eq/neq/gt/gte/lt/lte) или массив строк (для in). НИКОГДА null
            - Для диапазонов дат используй ДВА фильтра на одно поле: gte для начала, lte для конца. Формат дат: YYYY-MM-DD
            - "1 квартал 2025" → gte "2025-01-01" + lte "2025-03-31"; "2 квартал" → gte "2025-04-01" + lte "2025-06-30"
            - sum, avg, median, variance, stddev, int_sum, running_sum, sum_pct_* — только для числовых полей
            - count, count_distinct, list_distinct, original, first, last, count_pct_* — для любых полей

            ОПТИМИЗАЦИЯ ДЛЯ БОЛЬШИХ ДАТАСЕТОВ:
            Тебе будет указано количество строк в датасете (rowCount).
            - Если rowCount > 100 000: ИЗБЕГАЙ aggregation "original" — предпочитай "sum", "count", "avg" и т.д. для группировки данных. "original" на миллионах строк убивает производительность.
            - Если rowCount > 100 000 и пользователь просит «покажи всё» или сырые данные: ОБЯЗАТЕЛЬНО добавь фильтры, чтобы ограничить выборку. Предупреди в text, что показываешь часть данных.
            - Если rowCount > 1 000 000: ВСЕГДА используй агрегацию (count, sum, avg и т.д.), НИКОГДА "original". Объясни в text, почему группируешь.
            - list_distinct и median на больших датасетах (> 1 000 000) тоже тяжёлые — избегай их без фильтров.

            ФОРМАТ ОТВЕТА — строго JSON, без markdown-обёртки:
            {
              "text": "краткое описание что покажет таблица",
              "config": {
                "rows": [{"fieldId": "...", "name": "..."}],
                "columns": [],
                "values": [{"fieldId": "...", "name": "...", "aggregation": "original"}],
                "filters": []
              }
            }

            ПРИМЕРЫ:
            Запрос: "покажи продажи по регионам"
            → rows: регион, values: продажи с "original"

            Запрос: "сумма выручки по городам"
            → rows: город, values: выручка с "sum"

            Запрос: "средняя оценка по отделам"
            → rows: отдел, values: оценка с "avg"

            Запрос: "какие товары продаются в каждом регионе"
            → rows: регион, values: товар с "list_distinct"

            Запрос: "доля выручки каждого региона от общего"
            → rows: регион, values: выручка с "sum_pct_total"

            Запрос: "количество сделок в Балашихе во 2 квартале 2025"
            → rows: (подходящее поле), values: сделки с "count", filters: [{город eq "Балашиха"}, {дата gte "2025-04-01"}, {дата lte "2025-06-30"}]

            Запрос: "продажи за январь 2025"
            → filters: [{дата gte "2025-01-01"}, {дата lte "2025-01-31"}]

            Отвечай только JSON.
            """;

    @Nullable
    private final AiClient aiClient;
    private final DatasetService datasetService;
    private final PivotService pivotService;
    private final JsonMapper objectMapper;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;

    public AiChatService(@Nullable AiClient aiClient,
                         DatasetService datasetService,
                         PivotService pivotService,
                         JsonMapper objectMapper,
                         ChatSessionRepository sessionRepository,
                         ChatMessageRepository messageRepository,
                         UserRepository userRepository) {
        this.aiClient = aiClient;
        this.datasetService = datasetService;
        this.pivotService = pivotService;
        this.objectMapper = objectMapper;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;

        if (aiClient == null) {
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
        if (aiClient == null) {
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

    public ChatResponseDto processExternalMessage(String message, List<DatasetFieldDto> fields, long rowCount) {
        if (aiClient == null) {
            return fallbackResponseFromFields(message, fields);
        }
        return doProcessMessageWithFields(message, fields, rowCount, null);
    }

    private ChatResponseDto doProcessMessage(String message, UUID datasetId, @Nullable ChatSession session) {
        List<DatasetFieldDto> fields = datasetService.getFields(datasetId);
        long rowCount = datasetService.getRowCount(datasetId);
        return doProcessMessageWithFields(message, fields, rowCount, session);
    }

    private ChatResponseDto doProcessMessageWithFields(String message, List<DatasetFieldDto> fields, long rowCount, @Nullable ChatSession session) {
        String fieldsDescription = fields.stream()
                .map(f -> "  {id: \"" + f.id() + "\", name: \"" + f.name() +
                        "\", type: \"" + f.type().getValue() + "\", category: \"" + f.category() + "\"}")
                .collect(Collectors.joining("\n"));

        List<AiClient.Message> history = new ArrayList<>();
        if (session != null) {
            var recentMessages = messageRepository.findTop20BySessionIdOrderByCreatedAtDesc(session.getId());
            for (int i = recentMessages.size() - 1; i >= 0; i--) {
                var m = recentMessages.get(i);
                if (i == 0 && m.getRole().equals("user") && m.getText().equals(message)) continue;
                history.add(new AiClient.Message(m.getRole(), m.getText()));
            }
        }

        String userPrompt = "Доступные поля датасета:\n" + fieldsDescription +
                "\n\nКоличество строк в датасете (rowCount): " + rowCount +
                "\n\nЗапрос пользователя: " + message;

        log.info("Chat prompt: fields={}, rowCount={}, historySize={}, message='{}'",
                fields.size(), rowCount, history.size(), message);
        log.debug("Chat user prompt:\n{}", userPrompt);

        try {
            String response = aiClient.chatWithHistory(SYSTEM_PROMPT, history, userPrompt);

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

    public String explainTable(ExplainRequestDto request,
                               @Nullable ConnectionService connectionService,
                               @Nullable String userEmail) {
        PivotResultDto result;
        if (request.datasetId() != null) {
            result = pivotService.executeForExplain(request.datasetId(), request.config());
        } else if (request.connectionId() != null && connectionService != null && userEmail != null) {
            result = pivotService.executeExternalForExplain(
                    request.connectionId(), request.schema(), request.tableName(),
                    request.config(), connectionService, userEmail);
        } else {
            return "Не указан источник данных (datasetId или connectionId)";
        }

        if (aiClient == null) {
            return fallbackExplain(result);
        }

        String prompt = buildExplainPrompt(request.config(), result);
        log.info("Explain prompt length: {} chars, groups: {}", prompt.length(), result.rows().size());
        log.debug("Explain prompt:\n{}", prompt);

        try {
            return aiClient.chat("", prompt);
        } catch (Exception e) {
            log.error("AI explain error", e);
            return "Не удалось сгенерировать аналитику: " + e.getMessage();
        }
    }

    private static final int MAX_DUMP_CHARS = 50_000;

    private String buildExplainPrompt(PivotConfigDto config, PivotResultDto result) {
        var sb = new StringBuilder();
        sb.append("Проанализируй результаты сводной таблицы и дай краткие бизнес-выводы на русском языке.\n\n");

        try {
            sb.append("Конфигурация таблицы:\n").append(objectMapper.writeValueAsString(config)).append("\n\n");
        } catch (JacksonException ignored) {}

        sb.append("Строк в источнике: ").append(result.totalRows()).append("\n");
        sb.append("Групп в результате pivot: ").append(result.rows().size()).append("\n\n");

        if (result.totals() != null && !result.totals().isEmpty()) {
            sb.append("Итоги (по всем данным): ").append(result.totals()).append("\n\n");
        }

        var stats = computeStatistics(result);
        if (!stats.isEmpty()) {
            sb.append("Статистика:\n");
            for (var entry : stats.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }

        var dump = buildCompactDump(result);
        boolean truncated = false;
        if (!dump.isEmpty()) {
            for (var entry : dump.entrySet()) {
                sb.append("Все группы по ").append(entry.getKey()).append(" (desc):\n");
                sb.append("  ").append(entry.getValue()).append("\n\n");
                if (entry.getValue().contains("... ещё")) truncated = true;
            }
        }

        sb.append("""
                На основе полных данных выше дай 3-5 ключевых инсайтов. Укажи лидеров, аутсайдеров, значимые различия и тренды. Используй конкретные цифры.
                ФОРМАТ: простой текст без markdown-разметки. Без **, ##, ```. Каждый инсайт — отдельный абзац с тире в начале.""");

        if (truncated) {
            sb.append("""
                \n\nВАЖНО: данные были обрезаны из-за слишком большого числа групп. В последнем инсайте ОБЯЗАТЕЛЬНО порекомендуй пользователю укрупнить группировку (например, группировать по региону вместо города, по месяцу вместо дня) или добавить фильтры, чтобы получить более точный и полный анализ.""");
        }

        return sb.toString();
    }

    private Map<String, String> computeStatistics(PivotResultDto result) {
        if (result.rows().isEmpty()) return Map.of();

        Set<String> valueKeys = new LinkedHashSet<>();
        for (var row : result.rows()) {
            if (row.values() != null) valueKeys.addAll(row.values().keySet());
        }

        Map<String, String> stats = new LinkedHashMap<>();
        for (String key : valueKeys) {
            List<Double> nums = new ArrayList<>();
            for (var row : result.rows()) {
                Object val = row.values() != null ? row.values().get(key) : null;
                if (val instanceof Number n) {
                    nums.add(n.doubleValue());
                }
            }
            if (nums.isEmpty()) continue;

            nums.sort(Double::compareTo);
            int n = nums.size();
            double sum = nums.stream().mapToDouble(Double::doubleValue).sum();
            double avg = sum / n;
            double min = nums.getFirst();
            double max = nums.getLast();
            double median = percentile(nums, 50);
            double q1 = percentile(nums, 25);
            double q3 = percentile(nums, 75);
            double variance = nums.stream().mapToDouble(v -> (v - avg) * (v - avg)).sum() / n;
            double stddev = Math.sqrt(variance);

            stats.put(key, "count=%d, sum=%.2f, avg=%.2f, min=%.2f, max=%.2f, Q1=%.2f, median=%.2f, Q3=%.2f, stddev=%.2f"
                    .formatted(n, sum, avg, min, max, q1, median, q3, stddev));
        }
        return stats;
    }

    private static double percentile(List<Double> sorted, int p) {
        if (sorted.size() == 1) return sorted.getFirst();
        double rank = (p / 100.0) * (sorted.size() - 1);
        int lower = (int) Math.floor(rank);
        int upper = Math.min(lower + 1, sorted.size() - 1);
        double frac = rank - lower;
        return sorted.get(lower) + frac * (sorted.get(upper) - sorted.get(lower));
    }

    private Map<String, String> buildCompactDump(PivotResultDto result) {
        if (result.rows().isEmpty()) return Map.of();

        Set<String> valueKeys = new LinkedHashSet<>();
        for (var row : result.rows()) {
            if (row.values() != null) valueKeys.addAll(row.values().keySet());
        }

        Map<String, String> dump = new LinkedHashMap<>();
        for (String key : valueKeys) {
            var sorted = result.rows().stream()
                    .filter(r -> r.values() != null && r.values().get(key) != null)
                    .sorted((a, b) -> {
                        Object av = a.values().get(key), bv = b.values().get(key);
                        if (av instanceof Number an && bv instanceof Number bn)
                            return Double.compare(bn.doubleValue(), an.doubleValue());
                        return String.valueOf(bv).compareTo(String.valueOf(av));
                    })
                    .toList();

            var sb = new StringBuilder();
            int totalChars = 0;
            int shown = 0;
            int remaining = sorted.size();

            for (var row : sorted) {
                String keyLabel = String.join("/", row.keys());
                String entry = keyLabel + ": " + row.values().get(key);

                if (totalChars + entry.length() + 3 > MAX_DUMP_CHARS && shown > 0) {
                    sb.append(" | ... ещё ").append(remaining).append(" групп");
                    break;
                }

                if (shown > 0) sb.append(" | ");
                sb.append(entry);
                totalChars += entry.length() + 3;
                shown++;
                remaining--;
            }
            dump.put(key, sb.toString());
        }
        return dump;
    }

    private static String resolveFieldName(String fieldId, String name, Map<String, String> fieldNameMap) {
        if (name != null && !name.isBlank()) return name;
        return fieldNameMap.getOrDefault(fieldId, fieldId);
    }

    private static List<PivotFieldDto> sanitizeFields(List<PivotFieldDto> src, Map<String, String> fieldNameMap) {
        if (src == null) return List.of();
        return src.stream()
                .filter(f -> f.fieldId() != null && !f.fieldId().isBlank())
                .map(f -> new PivotFieldDto(f.fieldId(), resolveFieldName(f.fieldId(), f.name(), fieldNameMap)))
                .toList();
    }

    private ChatResponseDto sanitizeResponse(ChatResponseDto response, List<DatasetFieldDto> fields) {
        if (response.config() == null) return response;

        var config = response.config();
        var fieldNameMap = fields.stream()
                .collect(Collectors.toMap(DatasetFieldDto::id, DatasetFieldDto::name, (a, b) -> a));

        var filters = config.filters() == null ? List.<PivotFilterFieldDto>of() :
                config.filters().stream()
                        .filter(f -> f.fieldId() != null && !f.fieldId().isBlank())
                        .map(f -> new PivotFilterFieldDto(
                                f.fieldId(),
                                resolveFieldName(f.fieldId(), f.name(), fieldNameMap),
                                f.operator() != null ? f.operator() : com.prosto.analytics.model.FilterOperator.EQ,
                                f.filterValue() != null ? f.filterValue() : List.of()
                        )).toList();

        var rows = sanitizeFields(config.rows(), fieldNameMap);
        var columns = sanitizeFields(config.columns(), fieldNameMap);
        var values = config.values() == null ? List.<PivotValueFieldDto>of() :
                config.values().stream()
                        .filter(v -> v.fieldId() != null && !v.fieldId().isBlank())
                        .map(v -> new PivotValueFieldDto(
                                v.fieldId(),
                                resolveFieldName(v.fieldId(), v.name(), fieldNameMap),
                                v.aggregation() != null ? v.aggregation() : com.prosto.analytics.model.AggregationType.ORIGINAL
                        )).toList();

        // Ensure at least one value field
        if (values.isEmpty()) {
            var numericField = fields.stream()
                    .filter(f -> f.type() == com.prosto.analytics.model.FieldType.NUMBER)
                    .findFirst();
            if (numericField.isPresent()) {
                values = List.of(new PivotValueFieldDto(numericField.get().id(),
                        numericField.get().name(), com.prosto.analytics.model.AggregationType.ORIGINAL));
            } else if (!fields.isEmpty()) {
                values = List.of(new PivotValueFieldDto(fields.getFirst().id(),
                        fields.getFirst().name(), com.prosto.analytics.model.AggregationType.ORIGINAL));
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
                        com.prosto.analytics.model.AggregationType.ORIGINAL));

        var config = new PivotConfigDto(rows, List.of(), values, List.of());

        return new ChatResponseDto(
                "AI-ассистент не настроен (нет AI_API_KEY). Показываю базовую сводку по первым доступным полям.",
                config
        );
    }

    private String fallbackExplain(PivotResultDto result) {
        int rowCount = result.rows().size();
        int totalCount = result.totals() != null ? result.totals().size() : 0;
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
