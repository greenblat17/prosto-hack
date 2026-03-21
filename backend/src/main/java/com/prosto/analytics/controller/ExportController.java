package com.prosto.analytics.controller;

import com.prosto.analytics.dto.PivotResultDto;
import com.prosto.analytics.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Tag(name = "Export", description = "Экспорт результатов сводной таблицы")
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @Operation(summary = "Экспорт в CSV", description = "Экспортирует результат сводной таблицы в CSV-файл с разделителем ';'")
    @PostMapping("/csv")
    public ResponseEntity<byte[]> exportCsv(@RequestBody @Valid PivotResultDto result) {
        byte[] csv = exportService.exportCsv(result);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pivot_export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(csv.length)
                .body(csv);
    }

    @Operation(summary = "Экспорт в Excel", description = "Экспортирует результат сводной таблицы в XLSX с форматированием, автофильтром и итогами")
    @PostMapping("/excel")
    public ResponseEntity<byte[]> exportExcel(@RequestBody @Valid PivotResultDto result) throws IOException {
        byte[] xlsx = exportService.exportExcel(result);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pivot_export.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(xlsx.length)
                .body(xlsx);
    }
}
