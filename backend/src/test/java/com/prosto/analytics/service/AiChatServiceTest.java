package com.prosto.analytics.service;

import com.prosto.analytics.dto.*;
import com.prosto.analytics.model.AggregationType;
import com.prosto.analytics.model.FieldType;
import com.prosto.analytics.model.FilterOperator;
import com.prosto.analytics.repository.ChatMessageRepository;
import com.prosto.analytics.repository.ChatSessionRepository;
import com.prosto.analytics.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AiChatServiceTest {

    private AiChatService service;
    private JsonMapper objectMapper;

    private static final List<DatasetFieldDto> FIELDS = List.of(
            new DatasetFieldDto("city", "Город", FieldType.STRING, "dimension"),
            new DatasetFieldDto("revenue", "Выручка", FieldType.NUMBER, "measure"),
            new DatasetFieldDto("date", "Дата", FieldType.DATE, "dimension")
    );

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        service = new AiChatService(
                null,
                mock(DatasetService.class),
                mock(PivotService.class),
                objectMapper,
                mock(ChatSessionRepository.class),
                mock(ChatMessageRepository.class),
                mock(UserRepository.class)
        );
    }

    private ChatResponseDto callSanitize(ChatResponseDto response, List<DatasetFieldDto> fields) throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("sanitizeResponse", ChatResponseDto.class, List.class);
        m.setAccessible(true);
        return (ChatResponseDto) m.invoke(service, response, fields);
    }

    private ChatResponseDto callParse(String json) throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("parseResponse", String.class);
        m.setAccessible(true);
        return (ChatResponseDto) m.invoke(service, json);
    }

    private String callFixTruncated(String json) throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("fixTruncatedJson", String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, json);
    }

    @Nested
    @DisplayName("sanitizeResponse")
    class Sanitize {

        @Test
        @DisplayName("null config is passed through")
        void nullConfig() throws Exception {
            var response = new ChatResponseDto("hello", null);
            var result = callSanitize(response, FIELDS);
            assertThat(result.config()).isNull();
            assertThat(result.text()).isEqualTo("hello");
        }

        @Test
        @DisplayName("null name in filter is resolved from fields")
        void nullFilterName() throws Exception {
            var config = new PivotConfigDto(
                    List.of(new PivotFieldDto("city", "Город")),
                    List.of(),
                    List.of(new PivotValueFieldDto("revenue", "Выручка", AggregationType.SUM)),
                    List.of(new PivotFilterFieldDto("city", null, FilterOperator.EQ, List.of("Москва")))
            );
            var response = new ChatResponseDto("test", config);

            var result = callSanitize(response, FIELDS);

            assertThat(result.config().filters()).hasSize(1);
            assertThat(result.config().filters().getFirst().name()).isEqualTo("Город");
        }

        @Test
        @DisplayName("null name in rows is resolved from fields")
        void nullRowName() throws Exception {
            var config = new PivotConfigDto(
                    List.of(new PivotFieldDto("city", null)),
                    List.of(),
                    List.of(new PivotValueFieldDto("revenue", "Выручка", AggregationType.SUM)),
                    List.of()
            );
            var response = new ChatResponseDto("test", config);

            var result = callSanitize(response, FIELDS);

            assertThat(result.config().rows().getFirst().name()).isEqualTo("Город");
        }

        @Test
        @DisplayName("null name in values is resolved from fields")
        void nullValueName() throws Exception {
            var config = new PivotConfigDto(
                    List.of(new PivotFieldDto("city", "Город")),
                    List.of(),
                    List.of(new PivotValueFieldDto("revenue", null, AggregationType.SUM)),
                    List.of()
            );
            var response = new ChatResponseDto("test", config);

            var result = callSanitize(response, FIELDS);

            assertThat(result.config().values().getFirst().name()).isEqualTo("Выручка");
        }

        @Test
        @DisplayName("null aggregation defaults to ORIGINAL")
        void nullAggregation() throws Exception {
            var config = new PivotConfigDto(
                    List.of(new PivotFieldDto("city", "Город")),
                    List.of(),
                    List.of(new PivotValueFieldDto("revenue", "Выручка", null)),
                    List.of()
            );
            var response = new ChatResponseDto("test", config);

            var result = callSanitize(response, FIELDS);

            assertThat(result.config().values().getFirst().aggregation())
                    .isEqualTo(AggregationType.ORIGINAL);
        }

        @Test
        @DisplayName("null operator defaults to EQ")
        void nullOperator() throws Exception {
            var config = new PivotConfigDto(
                    List.of(new PivotFieldDto("city", "Город")),
                    List.of(),
                    List.of(new PivotValueFieldDto("revenue", "Выручка", AggregationType.SUM)),
                    List.of(new PivotFilterFieldDto("city", "Город", null, List.of("test")))
            );
            var response = new ChatResponseDto("test", config);

            var result = callSanitize(response, FIELDS);

            assertThat(result.config().filters().getFirst().operator())
                    .isEqualTo(FilterOperator.EQ);
        }

        @Test
        @DisplayName("null filterValue defaults to empty list")
        void nullFilterValue() throws Exception {
            var config = new PivotConfigDto(
                    List.of(new PivotFieldDto("city", "Город")),
                    List.of(),
                    List.of(new PivotValueFieldDto("revenue", "Выручка", AggregationType.SUM)),
                    List.of(new PivotFilterFieldDto("city", "Город", FilterOperator.EQ, null))
            );
            var response = new ChatResponseDto("test", config);

            var result = callSanitize(response, FIELDS);

            assertThat(result.config().filters().getFirst().filterValue()).isEmpty();
        }

        @Test
        @DisplayName("null rows/columns/values become empty lists")
        void nullLists() throws Exception {
            var config = new PivotConfigDto(null, null, null, null);
            var response = new ChatResponseDto("test", config);

            var result = callSanitize(response, FIELDS);

            assertThat(result.config().columns()).isEmpty();
            assertThat(result.config().filters()).isEmpty();
            assertThat(result.config().rows()).isNotEmpty();
            assertThat(result.config().values()).isNotEmpty();
        }

        @Test
        @DisplayName("blank fieldId in filter is dropped")
        void blankFieldIdDropped() throws Exception {
            var config = new PivotConfigDto(
                    List.of(new PivotFieldDto("city", "Город")),
                    List.of(),
                    List.of(new PivotValueFieldDto("revenue", "Выручка", AggregationType.SUM)),
                    List.of(new PivotFilterFieldDto("", "test", FilterOperator.EQ, List.of("x")))
            );
            var response = new ChatResponseDto("test", config);

            var result = callSanitize(response, FIELDS);

            assertThat(result.config().filters()).isEmpty();
        }

        @Test
        @DisplayName("empty values are auto-filled from numeric fields")
        void autoFillValues() throws Exception {
            var config = new PivotConfigDto(
                    List.of(new PivotFieldDto("city", "Город")),
                    List.of(),
                    List.of(),
                    List.of()
            );
            var response = new ChatResponseDto("test", config);

            var result = callSanitize(response, FIELDS);

            assertThat(result.config().values()).hasSize(1);
            assertThat(result.config().values().getFirst().fieldId()).isEqualTo("revenue");
        }

        @Test
        @DisplayName("empty rows are auto-filled from string fields")
        void autoFillRows() throws Exception {
            var config = new PivotConfigDto(
                    List.of(),
                    List.of(),
                    List.of(new PivotValueFieldDto("revenue", "Выручка", AggregationType.SUM)),
                    List.of()
            );
            var response = new ChatResponseDto("test", config);

            var result = callSanitize(response, FIELDS);

            assertThat(result.config().rows()).hasSize(1);
            assertThat(result.config().rows().getFirst().fieldId()).isEqualTo("city");
        }
    }

    @Nested
    @DisplayName("parseResponse")
    class Parse {

        @Test
        @DisplayName("valid JSON parsed successfully")
        void validJson() throws Exception {
            String json = """
                    {"text":"тест","config":{"rows":[{"fieldId":"city","name":"Город"}],"columns":[],"values":[{"fieldId":"revenue","name":"Выручка","aggregation":"sum"}],"filters":[]}}""";

            var result = callParse(json);

            assertThat(result.text()).isEqualTo("тест");
            assertThat(result.config()).isNotNull();
            assertThat(result.config().rows()).hasSize(1);
        }

        @Test
        @DisplayName("JSON wrapped in markdown code block")
        void markdownWrapped() throws Exception {
            String json = """
                    ```json
                    {"text":"тест","config":{"rows":[],"columns":[],"values":[{"fieldId":"revenue","name":"Выручка","aggregation":"count"}],"filters":[]}}
                    ```""";

            var result = callParse(json);

            assertThat(result.config()).isNotNull();
        }

        @Test
        @DisplayName("JSON embedded in text is extracted")
        void embeddedJson() throws Exception {
            String response = "Here is the config: {\"text\":\"result\",\"config\":{\"rows\":[],\"columns\":[],\"values\":[{\"fieldId\":\"rev\",\"name\":\"rev\",\"aggregation\":\"sum\"}],\"filters\":[]}} and done.";

            var result = callParse(response);

            assertThat(result.config()).isNotNull();
        }

        @Test
        @DisplayName("unparseable response returns text with null config")
        void unparseable() throws Exception {
            var result = callParse("This is just plain text, no JSON here.");

            assertThat(result.text()).contains("This is just plain text");
            assertThat(result.config()).isNull();
        }
    }

    @Nested
    @DisplayName("fixTruncatedJson")
    class FixTruncated {

        @Test
        @DisplayName("adds missing closing braces")
        void missingBraces() throws Exception {
            String truncated = "{\"text\":\"hello\",\"config\":{\"rows\":[]";

            var result = callFixTruncated(truncated);

            assertThat(result).endsWith("}}");
        }

        @Test
        @DisplayName("adds missing closing brackets")
        void missingBrackets() throws Exception {
            String truncated = "{\"values\":[{\"id\":\"a\"}";

            var result = callFixTruncated(truncated);

            assertThat(result).endsWith("]}");
        }

        @Test
        @DisplayName("complete JSON is unchanged")
        void completeJson() throws Exception {
            String complete = "{\"text\":\"ok\"}";

            assertThat(callFixTruncated(complete)).isEqualTo(complete);
        }
    }
}
