package com.prosto.analytics.controller;

import com.prosto.analytics.service.DatasetService;
import com.prosto.analytics.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DatasetController.class)
@AutoConfigureMockMvc(addFilters = false)
class DatasetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatasetService datasetService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /upload — 400 with empty file")
    void uploadEmptyFile() throws Exception {
        var file = new MockMultipartFile("file", "data.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/datasets/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Файл пустой"));
    }

    @Test
    @DisplayName("POST /upload — 400 with unsupported extension")
    void uploadUnsupportedFormat() throws Exception {
        var file = new MockMultipartFile("file", "data.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/datasets/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /upload — 400 with .json extension")
    void uploadJsonFile() throws Exception {
        var file = new MockMultipartFile("file", "data.json", "application/json", "{}".getBytes());

        mockMvc.perform(multipart("/api/datasets/upload").file(file))
                .andExpect(status().isBadRequest());
    }
}
