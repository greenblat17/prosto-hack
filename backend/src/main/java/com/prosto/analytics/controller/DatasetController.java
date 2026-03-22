package com.prosto.analytics.controller;

import com.prosto.analytics.dto.ColumnStatsDto;
import com.prosto.analytics.dto.DatasetFieldDto;
import com.prosto.analytics.dto.DatasetInfoDto;
import com.prosto.analytics.service.DatasetService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Tag(name = "Datasets", description = "Управление датасетами — загрузка CSV, получение полей")
@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("csv", "xlsx", "xls");

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @Operation(summary = "Список всех датасетов")
    @GetMapping
    public List<DatasetInfoDto> listDatasets() {
        return datasetService.listDatasets();
    }

    @Operation(summary = "Получить поля датасета", description = "Возвращает список полей с типами и категориями")
    @GetMapping("/{id}/fields")
    public List<DatasetFieldDto> getFields(@PathVariable UUID id) {
        return datasetService.getFields(id);
    }

    @Operation(summary = "Статистика по колонке", description = "Возвращает COUNT DISTINCT, nulls, min/max, топ-5 значений")
    @GetMapping("/{id}/fields/{fieldName}/stats")
    public ColumnStatsDto getColumnStats(@PathVariable UUID id, @PathVariable String fieldName) {
        return datasetService.getColumnStats(id, fieldName);
    }

    @Operation(summary = "Удалить датасет", description = "Удаляет датасет и его таблицу данных")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataset(@PathVariable UUID id) {
        datasetService.deleteDataset(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Загрузить файл данных", description = "Создаёт датасет из CSV/XLSX: определяет типы колонок, загружает данные через COPY")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DatasetInfoDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name) {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }

        String originalName = file.getOriginalFilename();
        if (originalName != null) {
            String ext = originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                    : "";
            if (!ALLOWED_EXTENSIONS.contains(ext)) {
                throw new IllegalArgumentException("Неподдерживаемый формат файла. Допустимо: CSV, XLSX, XLS");
            }
        }

        String datasetName = (name != null && !name.isBlank()) ? name : originalName;
        DatasetInfoDto result = datasetService.uploadFile(datasetName, file);
        return ResponseEntity.ok(result);
    }
}
