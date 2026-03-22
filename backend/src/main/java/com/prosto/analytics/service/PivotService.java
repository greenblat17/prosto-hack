package com.prosto.analytics.service;

import com.prosto.analytics.dto.*;
import com.prosto.analytics.model.AggregationType;
import com.prosto.analytics.model.Dataset;
import com.prosto.analytics.model.DatasetColumn;
import com.prosto.analytics.model.FieldType;
import com.prosto.analytics.repository.DatasetColumnRepository;
import com.prosto.analytics.repository.DatasetRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class PivotService {

    private static final Logger log = LoggerFactory.getLogger(PivotService.class);
    private static final String DATASET_NOT_FOUND = "Dataset not found: ";

    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository columnRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PivotSqlBuilder sqlBuilder;
    private final DuckDBCacheService duckDBCacheService;
    private final int maxResultRows;

    private final Cache<String, PivotResultDto> resultCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public PivotService(DatasetRepository datasetRepository,
                        DatasetColumnRepository columnRepository,
                        JdbcTemplate jdbcTemplate,
                        PivotSqlBuilder sqlBuilder,
                        DuckDBCacheService duckDBCacheService,
                        @Value("${app.pivot.max-result-rows:10000}") int maxResultRows) {
        this.datasetRepository = datasetRepository;
        this.columnRepository = columnRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.sqlBuilder = sqlBuilder;
        this.duckDBCacheService = duckDBCacheService;
        this.maxResultRows = maxResultRows;
    }

    public PivotResultDto execute(PivotExecuteRequestDto request) {
        Dataset dataset = datasetRepository.findById(request.datasetId())
                .orElseThrow(() -> new NoSuchElementException(DATASET_NOT_FOUND + request.datasetId()));

        List<DatasetColumn> columns = columnRepository.findByDatasetIdOrderByOrdinal(dataset.getId());
        Set<String> validColumns = columns.stream()
                .map(DatasetColumn::getColumnName)
                .collect(Collectors.toSet());

        PivotConfigDto config = request.config();

        Map<String, FieldType> columnTypes = columns.stream()
                .collect(Collectors.toMap(DatasetColumn::getColumnName, DatasetColumn::getFieldType));
        for (var vf : config.values()) {
            if (vf.aggregation().requiresNumericColumn()) {
                FieldType ft = columnTypes.get(vf.fieldId());
                if (ft != null && ft != FieldType.NUMBER) {
                    throw new IllegalArgumentException(
                            "Агрегация «" + vf.aggregation().getDisplayLabel() + "» требует числовое поле; используйте «Количество» для «" + vf.name() + "»");
                }
            }
        }
        int offset = Math.max(0, request.offset() != null ? request.offset() : 0);
        int limit = Math.clamp(request.limit() != null ? request.limit() : maxResultRows, 1, maxResultRows);

        return executePivotQueries(jdbcTemplate, config, dataset.getTableName(), validColumns,
                SqlDialect.POSTGRESQL, offset, limit);
    }

    public PivotResultDto executeExternal(ExternalPivotRequestDto request, ConnectionService connectionService, String userEmail) {
        String connId = request.connectionId();
        String schema = request.schema();
        String table = request.tableName();
        PivotConfigDto config = request.config();

        // Validate fields (always from external DB metadata)
        List<TableFieldDto> fields = connectionService.getTableFields(connId, schema, table, userEmail);
        Set<String> validColumns = fields.stream().map(TableFieldDto::name).collect(Collectors.toSet());

        // Get stable connection info for cache lookup (survives reconnection)
        ConnectionService.ConnectionInfo connInfo = connectionService.getConnectionInfo(connId, userEmail);

        // Determine target: DuckDB cache or external PostgreSQL
        boolean useDuckDB = duckDBCacheService.isTableCached(
                connInfo.host(), connInfo.port(), connInfo.database(), schema, table);

        JdbcTemplate jdbc;
        String tableName;
        SqlDialect dialect;

        if (useDuckDB) {
            jdbc = duckDBCacheService.getDuckDBJdbc();
            tableName = duckDBCacheService.cacheTableName(
                    connInfo.host(), connInfo.port(), connInfo.database(), schema, table);
            dialect = SqlDialect.DUCKDB;
            log.info("Pivot from DuckDB cache: {}.{}", schema, table);
        } else {
            jdbc = connectionService.getJdbc(connId, userEmail);
            tableName = sqlBuilder.qualifiedTable(schema, table);
            dialect = SqlDialect.POSTGRESQL;
            log.info("Pivot from PostgreSQL: {}.{}", schema, table);
            // Trigger background caching for next time
            duckDBCacheService.cacheTableAsync(connId, schema, table, connectionService, userEmail);
        }

        int offset = Math.max(0, request.offset() != null ? request.offset() : 0);
        int limit = Math.clamp(request.limit() != null ? request.limit() : maxResultRows, 1, maxResultRows);

        return executePivotQueries(jdbc, config, tableName, validColumns, dialect, offset, limit);
    }

    public String previewExternalSql(ExternalPivotRequestDto request, ConnectionService connectionService, String userEmail) {
        connectionService.getJdbc(request.connectionId(), userEmail);
        String tableName = sqlBuilder.qualifiedTable(request.schema(), request.tableName());
        return sqlBuilder.buildPreviewSql(request.config(), tableName);
    }

    public String previewSql(PivotExecuteRequestDto request) {
        Dataset dataset = datasetRepository.findById(request.datasetId())
                .orElseThrow(() -> new NoSuchElementException(DATASET_NOT_FOUND + request.datasetId()));
        return sqlBuilder.buildPreviewSql(request.config(), dataset.getTableName());
    }

    private static final int EXPLAIN_LIMIT = 2000;

    public PivotResultDto executeForExplain(UUID datasetId, PivotConfigDto config) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new NoSuchElementException(DATASET_NOT_FOUND + datasetId));

        Set<String> validColumns = columnRepository.findByDatasetIdOrderByOrdinal(dataset.getId())
                .stream().map(DatasetColumn::getColumnName).collect(Collectors.toSet());

        var mainQuery = sqlBuilder.buildPivotQuery(config, dataset.getTableName(), validColumns, 0, EXPLAIN_LIMIT);
        var totalsQuery = sqlBuilder.buildTotalsQuery(config, dataset.getTableName(), validColumns);

        var mainFuture = CompletableFuture.supplyAsync(
                () -> jdbcTemplate.queryForList(mainQuery.sql(), mainQuery.params().toArray()));
        var totalsFuture = CompletableFuture.supplyAsync(
                () -> jdbcTemplate.queryForList(totalsQuery.sql(), totalsQuery.params().toArray()));

        CompletableFuture.allOf(mainFuture, totalsFuture).join();

        return transformResult(mainFuture.join(), totalsFuture.join(), config,
                dataset.getRowCount(), 0, EXPLAIN_LIMIT);
    }

    public PivotResultDto executeExternalForExplain(String connectionId, String schema, String tableName,
                                                     PivotConfigDto config,
                                                     ConnectionService connectionService, String userEmail) {
        List<TableFieldDto> fields = connectionService.getTableFields(connectionId, schema, tableName, userEmail);
        Set<String> validColumns = fields.stream().map(TableFieldDto::name).collect(Collectors.toSet());

        ConnectionService.ConnectionInfo connInfo = connectionService.getConnectionInfo(connectionId, userEmail);
        boolean useDuckDB = duckDBCacheService.isTableCached(
                connInfo.host(), connInfo.port(), connInfo.database(), schema, tableName);

        JdbcTemplate jdbc;
        String qualifiedTable;
        SqlDialect dialect;

        if (useDuckDB) {
            jdbc = duckDBCacheService.getDuckDBJdbc();
            qualifiedTable = duckDBCacheService.cacheTableName(
                    connInfo.host(), connInfo.port(), connInfo.database(), schema, tableName);
            dialect = SqlDialect.DUCKDB;
        } else {
            jdbc = connectionService.getJdbc(connectionId, userEmail);
            qualifiedTable = sqlBuilder.qualifiedTable(schema, tableName);
            dialect = SqlDialect.POSTGRESQL;
        }

        var mainQuery = sqlBuilder.buildPivotQuery(config, qualifiedTable, validColumns, 0, EXPLAIN_LIMIT, dialect);
        var totalsQuery = sqlBuilder.buildTotalsQuery(config, qualifiedTable, validColumns, dialect);

        final JdbcTemplate finalJdbc = jdbc;
        var mainFuture = CompletableFuture.supplyAsync(
                () -> finalJdbc.queryForList(mainQuery.sql(), mainQuery.params().toArray()));
        var totalsFuture = CompletableFuture.supplyAsync(
                () -> finalJdbc.queryForList(totalsQuery.sql(), totalsQuery.params().toArray()));

        CompletableFuture.allOf(mainFuture, totalsFuture).join();

        long rowCount = connectionService.getTableRowCount(connectionId, schema, tableName, userEmail);
        return transformResult(mainFuture.join(), totalsFuture.join(), config, rowCount, 0, EXPLAIN_LIMIT);
    }

    private PivotResultDto executePivotQueries(JdbcTemplate jdbc, PivotConfigDto config,
                                               String tableName, Set<String> validColumns,
                                               SqlDialect dialect, int offset, int limit) {
        String cacheKey = buildCacheKey(tableName, config, offset, limit);
        PivotResultDto cached = resultCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.info("Pivot cache hit: {}", tableName);
            return cached;
        }

        var countQuery = sqlBuilder.buildCountQuery(config, tableName, validColumns, dialect);
        var mainQuery = sqlBuilder.buildPivotQuery(config, tableName, validColumns, offset, limit, dialect);
        var totalsQuery = sqlBuilder.buildTotalsQuery(config, tableName, validColumns, dialect);

        var countFuture = CompletableFuture.supplyAsync(
                () -> jdbc.queryForObject(countQuery.sql(), Long.class, countQuery.params().toArray()));
        var mainFuture = CompletableFuture.supplyAsync(
                () -> jdbc.queryForList(mainQuery.sql(), mainQuery.params().toArray()));
        var totalsFuture = CompletableFuture.supplyAsync(
                () -> jdbc.queryForList(totalsQuery.sql(), totalsQuery.params().toArray()));

        CompletableFuture.allOf(countFuture, mainFuture, totalsFuture).join();

        Long totalRows = countFuture.join();
        List<Map<String, Object>> rawRows = mainFuture.join();
        List<Map<String, Object>> totalsRaw = totalsFuture.join();

        PivotResultDto result = transformResult(rawRows, totalsRaw, config,
                totalRows != null ? totalRows : 0, offset, limit);
        resultCache.put(cacheKey, result);
        return result;
    }

    private PivotResultDto transformResult(List<Map<String, Object>> rawRows,
                                           List<Map<String, Object>> totalsRaw,
                                           PivotConfigDto config,
                                           long totalRows, int offset, int limit) {
        boolean allOriginal = config.values().stream()
                .allMatch(v -> v.aggregation() == AggregationType.ORIGINAL);

        if (allOriginal) {
            return transformOriginalResult(rawRows, config, totalRows, offset, limit);
        }

        List<String> rowFieldIds = config.rows().stream().map(PivotFieldDto::fieldId).toList();
        List<String> colFieldIds = config.columns().stream().map(PivotFieldDto::fieldId).toList();

        Set<String> colKeySet = new LinkedHashSet<>();
        for (var row : rawRows) {
            if (!colFieldIds.isEmpty()) {
                colKeySet.add(buildColKey(row, colFieldIds));
            }
        }
        List<String> colKeys = colKeySet.isEmpty() ? List.of("") : new ArrayList<>(colKeySet);

        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (var row : rawRows) {
            String rowKey = rowFieldIds.isEmpty()
                    ? "__total__"
                    : rowFieldIds.stream().map(id -> str(row.get(id))).collect(Collectors.joining("|||"));
            groups.computeIfAbsent(rowKey, k -> new ArrayList<>()).add(row);
        }

        List<PivotResultRowDto> resultRows = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            List<String> keys = entry.getKey().equals("__total__")
                    ? List.of("Итого")
                    : List.of(entry.getKey().split("\\|\\|\\|", -1));

            Map<String, Object> values = buildValueMap(entry.getValue(), colFieldIds, config.values());
            resultRows.add(new PivotResultRowDto(keys, values, null));
        }

        resultRows.sort(Comparator.comparing(r -> r.keys().isEmpty() ? "" : r.keys().getFirst()));

        Map<String, Object> totals = buildTotalsMap(totalsRaw, colFieldIds, config.values());

        List<List<String>> columnHeaders = colKeys.stream()
                .map(k -> k.isEmpty() ? List.<String>of() : List.of(k.split(" / ", -1)))
                .toList();

        return new PivotResultDto(columnHeaders, resultRows, totals, totalRows, offset, limit);
    }

    private PivotResultDto transformOriginalResult(List<Map<String, Object>> rawRows,
                                                   PivotConfigDto config,
                                                   long totalRows, int offset, int limit) {
        List<String> rowFieldIds = config.rows().stream().map(PivotFieldDto::fieldId).toList();
        List<String> colFieldIds = config.columns().stream().map(PivotFieldDto::fieldId).toList();
        List<String> allKeyIds = new ArrayList<>(rowFieldIds);
        allKeyIds.addAll(colFieldIds);

        List<PivotResultRowDto> resultRows = new ArrayList<>();
        for (var row : rawRows) {
            List<String> keys = allKeyIds.stream().map(id -> str(row.get(id))).toList();

            Map<String, Object> values = new LinkedHashMap<>();
            for (var vf : config.values()) {
                String aggKey = vf.fieldId() + "_" + vf.aggregation().getValue();
                Object val = row.get(aggKey);
                if (val == null) val = row.get(vf.fieldId());
                values.put(vf.name(), toValue(val, vf.aggregation()));
            }
            resultRows.add(new PivotResultRowDto(keys, values, null));
        }

        return new PivotResultDto(List.of(), resultRows, Map.of(), totalRows, offset, limit);
    }

    private String buildValueLabel(PivotValueFieldDto vf) {
        if (vf.aggregation() == AggregationType.ORIGINAL) {
            return vf.name();
        }
        return vf.name() + " (" + vf.aggregation().getDisplayLabel() + ")";
    }

    private Map<String, Object> buildValueMap(List<Map<String, Object>> groupRows,
                                              List<String> colFieldIds,
                                              List<PivotValueFieldDto> valueFields) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (var row : groupRows) {
            String colKey = colFieldIds.isEmpty() ? "" : buildColKey(row, colFieldIds);
            for (var vf : valueFields) {
                String aggKey = vf.fieldId() + "_" + vf.aggregation().getValue();
                String valueName = buildValueLabel(vf);
                String label;
                if (colKey.isEmpty()) {
                    label = valueName;
                } else if (valueFields.size() == 1) {
                    label = colKey;
                } else {
                    label = colKey + " | " + valueName;
                }
                values.put(label, toValue(row.get(aggKey), vf.aggregation()));
            }
        }
        return values;
    }

    private Map<String, Object> buildTotalsMap(List<Map<String, Object>> totalsRaw,
                                               List<String> colFieldIds,
                                               List<PivotValueFieldDto> valueFields) {
        Map<String, Object> totals = new LinkedHashMap<>();
        for (var row : totalsRaw) {
            String colKey = colFieldIds.isEmpty() ? "" : buildColKey(row, colFieldIds);
            for (var vf : valueFields) {
                String aggKey = vf.fieldId() + "_" + vf.aggregation().getValue();
                String valueName = buildValueLabel(vf);
                String label;
                if (colKey.isEmpty()) {
                    label = valueName;
                } else if (valueFields.size() == 1) {
                    label = colKey;
                } else {
                    label = colKey + " | " + valueName;
                }
                totals.put(label, toValue(row.get(aggKey), vf.aggregation()));
            }
        }
        return totals;
    }

    private String buildColKey(Map<String, Object> row, List<String> colFieldIds) {
        return colFieldIds.stream().map(id -> str(row.get(id))).collect(Collectors.joining(" / "));
    }

    private String str(Object val) {
        return val == null ? "" : val.toString();
    }

    private Object toValue(Object val, AggregationType agg) {
        if (agg == AggregationType.ORIGINAL) {
            if (val == null) return null;
            if (val instanceof Number n) return Math.round(n.doubleValue() * 100.0) / 100.0;
            return val.toString();
        }
        if (agg.returnsText()) {
            return val != null ? val.toString() : "";
        }
        if (val == null) return 0.0;
        if (val instanceof Number n) return Math.round(n.doubleValue() * 100.0) / 100.0;
        if (agg.requiresNumericColumn()) return toDouble(val);
        return val.toString();
    }

    private String buildCacheKey(String tableName, PivotConfigDto config, int offset, int limit) {
        try {
            String raw = tableName + "|" + config.toString() + "|" + offset + "|" + limit;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.trace("SHA-256 unavailable, using hashCode: {}", e.getMessage());
            return tableName + "|" + config.hashCode() + "|" + offset + "|" + limit;
        }
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return Math.round(n.doubleValue() * 100.0) / 100.0;
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            log.trace("Cannot parse '{}' as double: {}", val, e.getMessage());
            return 0.0;
        }
    }
}
