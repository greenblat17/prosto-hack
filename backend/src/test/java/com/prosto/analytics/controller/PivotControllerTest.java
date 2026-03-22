package com.prosto.analytics.controller;

import com.prosto.analytics.dto.PivotResultDto;
import com.prosto.analytics.service.ConnectionService;
import com.prosto.analytics.service.JwtService;
import com.prosto.analytics.service.PivotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PivotController.class)
@AutoConfigureMockMvc(addFilters = false)
class PivotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PivotService pivotService;

    @MockitoBean
    private ConnectionService connectionService;

    @MockitoBean
    private JwtService jwtService;

    private static final String VALID_REQUEST = """
            {
              "datasetId": "00000000-0000-0000-0000-000000000001",
              "config": {
                "rows": [{"fieldId": "city", "name": "Город"}],
                "columns": [],
                "values": [{"fieldId": "revenue", "name": "Выручка", "aggregation": "sum"}],
                "filters": []
              }
            }""";

    @Test
    @DisplayName("POST /execute — 200 with valid request")
    void executeSuccess() throws Exception {
        when(pivotService.execute(any()))
                .thenReturn(new PivotResultDto(List.of(), List.of(), Map.of(), 0, 0, 100));

        mockMvc.perform(post("/api/pivot/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(0));
    }

    @Test
    @DisplayName("POST /execute — 400 with null datasetId")
    void executeNullDatasetId() throws Exception {
        mockMvc.perform(post("/api/pivot/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "config": {
                                    "rows": [{"fieldId": "city", "name": "Город"}],
                                    "columns": [],
                                    "values": [{"fieldId": "revenue", "name": "Выручка", "aggregation": "sum"}],
                                    "filters": []
                                  }
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("datasetId: must not be null"));
    }

    @Test
    @DisplayName("POST /execute — 400 with null rows")
    void executeNullRows() throws Exception {
        mockMvc.perform(post("/api/pivot/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetId": "00000000-0000-0000-0000-000000000001",
                                  "config": {
                                    "columns": [],
                                    "values": [{"fieldId": "revenue", "name": "Выручка", "aggregation": "sum"}],
                                    "filters": []
                                  }
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /execute — 400 with null aggregation")
    void executeNullAggregation() throws Exception {
        mockMvc.perform(post("/api/pivot/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetId": "00000000-0000-0000-0000-000000000001",
                                  "config": {
                                    "rows": [{"fieldId": "city", "name": "Город"}],
                                    "columns": [],
                                    "values": [{"fieldId": "revenue", "name": "Выручка"}],
                                    "filters": []
                                  }
                                }"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /execute — 400 with blank field name")
    void executeBlankFieldName() throws Exception {
        mockMvc.perform(post("/api/pivot/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetId": "00000000-0000-0000-0000-000000000001",
                                  "config": {
                                    "rows": [{"fieldId": "", "name": "Город"}],
                                    "columns": [],
                                    "values": [{"fieldId": "revenue", "name": "Выручка", "aggregation": "sum"}],
                                    "filters": []
                                  }
                                }"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /sql — 200 with valid request")
    void previewSqlSuccess() throws Exception {
        when(pivotService.previewSql(any())).thenReturn("SELECT city, SUM(revenue) FROM sales GROUP BY city");

        mockMvc.perform(post("/api/pivot/sql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sql").isNotEmpty());
    }
}
