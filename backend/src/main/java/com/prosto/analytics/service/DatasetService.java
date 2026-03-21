package com.prosto.analytics.service;

import com.prosto.analytics.dto.ColumnStatsDto;
import com.prosto.analytics.dto.DatasetFieldDto;
import com.prosto.analytics.dto.DatasetInfoDto;
import com.prosto.analytics.model.Dataset;
import com.prosto.analytics.model.DatasetColumn;
import com.prosto.analytics.model.FieldType;
import com.prosto.analytics.repository.DatasetColumnRepository;
import com.prosto.analytics.repository.DatasetRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DatasetService {

    private static final Logger log = LoggerFactory.getLogger(DatasetService.class);
    private static final int SAMPLE_SIZE = 200;

    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository columnRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public DatasetService(DatasetRepository datasetRepository,
                          DatasetColumnRepository columnRepository,
                          JdbcTemplate jdbcTemplate,
                          DataSource dataSource) {
        this.datasetRepository = datasetRepository;
        this.columnRepository = columnRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Transactional
    public void deleteDataset(UUID id) {
        Dataset dataset = datasetRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Dataset not found: " + id));
        String tableName = dataset.getTableName();
        datasetRepository.delete(dataset);
        datasetRepository.flush();
        jdbcTemplate.execute("DROP TABLE IF EXISTS \"" + tableName + "\"");
        log.info("Dataset '{}' deleted, table {} dropped", dataset.getName(), tableName);
    }

    @Transactional(readOnly = true)
    public List<DatasetInfoDto> listDatasets() {
        return datasetRepository.findAll().stream()
                .map(d -> new DatasetInfoDto(d.getId(), d.getName(), d.getRowCount(),
                        d.getColumns().size(), d.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DatasetFieldDto> getFields(UUID datasetId) {
        if (!datasetRepository.existsById(datasetId)) {
            throw new NoSuchElementException("Dataset not found: " + datasetId);
        }
        return columnRepository.findByDatasetIdOrderByOrdinal(datasetId).stream()
                .map(c -> new DatasetFieldDto(c.getColumnName(), c.getDisplayName(),
                        c.getFieldType(), c.getCategory()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ColumnStatsDto getColumnStats(UUID datasetId, String columnName) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new NoSuchElementException("Dataset not found: " + datasetId));

        // Validate column exists
        boolean exists = dataset.getColumns().stream()
                .anyMatch(c -> c.getColumnName().equals(columnName));
        if (!exists) {
            throw new IllegalArgumentException("Column not found: " + columnName);
        }

        String table = "\"" + dataset.getTableName() + "\"";
        String col = "\"" + columnName.replace("\"", "\"\"") + "\"";

        // Basic stats
        var stats = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS total, COUNT(DISTINCT " + col + ") AS distinct_cnt, " +
                "COUNT(*) - COUNT(" + col + ") AS null_cnt, " +
                "MIN(" + col + ") AS min_val, MAX(" + col + ") AS max_val " +
                "FROM " + table
        );

        long total = ((Number) stats.get("total")).longValue();
        long distinctCnt = ((Number) stats.get("distinct_cnt")).longValue();
        long nullCnt = ((Number) stats.get("null_cnt")).longValue();
        String minVal = stats.get("min_val") != null ? stats.get("min_val").toString() : null;
        String maxVal = stats.get("max_val") != null ? stats.get("max_val").toString() : null;

        // Top 5 values
        var topRows = jdbcTemplate.queryForList(
                "SELECT " + col + "::text AS val, COUNT(*) AS cnt FROM " + table +
                " WHERE " + col + " IS NOT NULL GROUP BY " + col +
                " ORDER BY COUNT(*) DESC LIMIT 5"
        );

        var topValues = topRows.stream()
                .map(r -> new ColumnStatsDto.TopValue(
                        r.get("val") != null ? r.get("val").toString() : "",
                        ((Number) r.get("cnt")).longValue()
                ))
                .toList();

        return new ColumnStatsDto(total, distinctCnt, nullCnt, minVal, maxVal, topValues);
    }

    @Transactional
    public DatasetInfoDto uploadFile(String name, MultipartFile file) throws Exception {
        String originalName = file.getOriginalFilename();
        if (originalName != null && (originalName.endsWith(".xlsx") || originalName.endsWith(".xls"))) {
            return uploadXlsx(name, file);
        }
        return uploadCsv(name, file);
    }

    @Transactional
    public DatasetInfoDto uploadCsv(String name, MultipartFile file) throws Exception {
        Path tempFile = Files.createTempFile("dataset-", ".csv");
        try {
            file.transferTo(tempFile);
            return uploadCsvFromPath(name, tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private DatasetInfoDto uploadXlsx(String name, MultipartFile file) throws Exception {
        Path xlsxFile = Files.createTempFile("dataset-", ".xlsx");
        Path csvFile = null;
        try {
            file.transferTo(xlsxFile);
            csvFile = convertXlsxToCsv(xlsxFile);
            return uploadCsvFromPath(name, csvFile);
        } finally {
            Files.deleteIfExists(xlsxFile);
            if (csvFile != null) Files.deleteIfExists(csvFile);
        }
    }

    private Path convertXlsxToCsv(Path xlsxPath) throws Exception {
        Path csvPath = Files.createTempFile("xlsx-converted-", ".csv");
        try (Workbook workbook = WorkbookFactory.create(xlsxPath.toFile());
             var writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8);
             var printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("XLSX file has no sheets");
            }
            if (workbook.getNumberOfSheets() > 1) {
                log.warn("Workbook has {} sheets, using first sheet only", workbook.getNumberOfSheets());
            }

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                throw new IllegalArgumentException("XLSX sheet has no rows");
            }
            DataFormatter dataFormatter = new DataFormatter();
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (Row row : sheet) {
                var values = new ArrayList<String>();
                int lastCol = sheet.getRow(0) != null ? sheet.getRow(0).getLastCellNum() : row.getLastCellNum();
                for (int c = 0; c < lastCol; c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell == null) {
                        values.add("");
                        continue;
                    }
                    values.add(formatCellValue(cell, dataFormatter, dateFmt));
                }
                printer.printRecord(values);
            }
        }
        return csvPath;
    }

    private String formatCellValue(Cell cell, DataFormatter dataFormatter, DateTimeFormatter dateFmt) {
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    var date = cell.getLocalDateTimeCellValue();
                    yield date != null ? date.toLocalDate().format(dateFmt) : "";
                }
                yield BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
            }
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield dataFormatter.formatCellValue(cell);
                } catch (Exception e) {
                    yield "";
                }
            }
            default -> "";
        };
    }

    private DatasetInfoDto uploadCsvFromPath(String name, Path csvFile) throws Exception {
        String tableName = "ds_" + UUID.randomUUID().toString().replace("-", "");
        boolean tableCreated = false;

        try {
            List<String> headers;
            List<FieldType> types;

            try (var reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8);
                 var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

                headers = deduplicateHeaders(
                        parser.getHeaderNames().stream().map(this::sanitizeColumnName).toList()
                );

                if (headers.isEmpty()) {
                    throw new IllegalArgumentException("File has no columns");
                }

                List<CSVRecord> sample = new ArrayList<>();
                for (CSVRecord record : parser) {
                    sample.add(record);
                    if (sample.size() >= SAMPLE_SIZE) break;
                }

                if (sample.isEmpty()) {
                    throw new IllegalArgumentException("File has no data rows");
                }

                types = detectTypes(headers, sample);
            }

            createTable(tableName, headers, types);
            tableCreated = true;

            long rowCount = copyData(tableName, headers, csvFile);

            Dataset dataset = new Dataset();
            dataset.setName(name);
            dataset.setTableName(tableName);
            dataset.setRowCount(rowCount);

            for (int i = 0; i < headers.size(); i++) {
                DatasetColumn col = new DatasetColumn();
                col.setDataset(dataset);
                col.setColumnName(headers.get(i));
                col.setDisplayName(headers.get(i));
                col.setFieldType(types.get(i));
                col.setCategory(categorize(types.get(i)));
                col.setOrdinal(i);
                dataset.getColumns().add(col);
            }

            datasetRepository.save(dataset);

            createIndexes(tableName, headers, types);

            log.info("Dataset '{}' uploaded: {} columns, {} rows, table={}", name, headers.size(), rowCount, tableName);

            return new DatasetInfoDto(dataset.getId(), dataset.getName(), rowCount,
                    headers.size(), dataset.getCreatedAt());
        } catch (Exception e) {
            if (tableCreated) {
                try {
                    jdbcTemplate.execute("DROP TABLE IF EXISTS \"" + tableName + "\"");
                    log.info("Cleaned up orphan table: {}", tableName);
                } catch (Exception dropEx) {
                    log.error("Failed to drop orphan table {}: {}", tableName, dropEx.getMessage());
                }
            }
            throw e;
        }
    }

    private void createTable(String tableName, List<String> headers, List<FieldType> types) {
        var columns = new StringBuilder();
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) columns.append(", ");
            columns.append('"').append(headers.get(i)).append("\" ").append(types.get(i).toSqlType());
        }
        jdbcTemplate.execute("CREATE TABLE \"" + tableName + "\" (" + columns + ")");
    }

    private long copyData(String tableName, List<String> headers, Path csvFile) throws Exception {
        String columnList = headers.stream().map(h -> "\"" + h + "\"").collect(Collectors.joining(", "));
        String copyCmd = "COPY \"" + tableName + "\" (" + columnList + ") FROM STDIN WITH (FORMAT csv, HEADER true, NULL '')";

        var conn = DataSourceUtils.getConnection(dataSource);
        try {
            BaseConnection pgConn = conn.unwrap(BaseConnection.class);
            CopyManager copyManager = new CopyManager(pgConn);
            try (var reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
                return copyManager.copyIn(copyCmd, reader);
            }
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    private void createIndexes(String tableName, List<String> headers, List<FieldType> types) {
        for (int i = 0; i < headers.size(); i++) {
            if (types.get(i) == FieldType.STRING) {
                try {
                    jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS \"idx_" + tableName + "_" + headers.get(i) +
                            "\" ON \"" + tableName + "\" (\"" + headers.get(i) + "\")");
                } catch (Exception e) {
                    log.warn("Failed to create index on {}.{}: {}", tableName, headers.get(i), e.getMessage());
                }
            }
        }
    }

    private List<FieldType> detectTypes(List<String> headers, List<CSVRecord> sample) {
        List<FieldType> types = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            types.add(detectColumnType(sample, i));
        }
        return types;
    }

    private FieldType detectColumnType(List<CSVRecord> sample, int colIndex) {
        boolean allNumeric = true;
        boolean allDate = true;
        boolean allBoolean = true;
        int nonEmpty = 0;

        for (CSVRecord record : sample) {
            if (colIndex >= record.size()) continue;
            String val = record.get(colIndex).trim();
            if (val.isEmpty()) continue;
            nonEmpty++;

            if (allNumeric && !isNumeric(val)) allNumeric = false;
            if (allDate && !isDate(val)) allDate = false;
            if (allBoolean && !isBoolean(val)) allBoolean = false;
        }

        if (nonEmpty == 0) return FieldType.STRING;
        if (allBoolean) return FieldType.BOOLEAN;
        if (allNumeric) return FieldType.NUMBER;
        if (allDate) return FieldType.DATE;
        return FieldType.STRING;
    }

    private boolean isNumeric(String val) {
        try { Double.parseDouble(val.replace(",", ".")); return true; }
        catch (NumberFormatException e) { return false; }
    }

    private boolean isDate(String val) {
        return val.matches("\\d{4}-\\d{2}-\\d{2}.*") ||
               val.matches("\\d{2}\\.\\d{2}\\.\\d{4}.*") ||
               val.matches("\\d{2}/\\d{2}/\\d{4}.*");
    }

    private boolean isBoolean(String val) {
        String lower = val.toLowerCase();
        return lower.equals("true") || lower.equals("false") ||
               lower.equals("yes") || lower.equals("no") ||
               lower.equals("1") || lower.equals("0");
    }

    private List<String> deduplicateHeaders(List<String> headers) {
        var seen = new HashMap<String, Integer>();
        var result = new ArrayList<String>(headers.size());
        for (String h : headers) {
            if (seen.containsKey(h)) {
                int count = seen.get(h) + 1;
                seen.put(h, count);
                result.add(h + "_" + count);
            } else {
                seen.put(h, 1);
                result.add(h);
            }
        }
        return result;
    }

    private String sanitizeColumnName(String header) {
        String sanitized = header.trim()
                .toLowerCase()
                .replaceAll("[^a-zA-Zа-яА-Я0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return sanitized.isEmpty() ? "col" : sanitized;
    }

    private String categorize(FieldType type) {
        return switch (type) {
            case NUMBER -> "Метрики";
            case DATE -> "Время";
            case BOOLEAN -> "Флаги";
            case STRING -> "Измерения";
        };
    }
}
