package com.prosto.analytics.service;

import com.prosto.analytics.dto.*;
import com.prosto.analytics.model.AggregationType;
import com.prosto.analytics.model.Dataset;
import com.prosto.analytics.model.DatasetColumn;
import com.prosto.analytics.model.FieldType;
import com.prosto.analytics.repository.DatasetColumnRepository;
import com.prosto.analytics.repository.DatasetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PivotService {

    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository columnRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PivotSqlBuilder sqlBuilder;

    @Value("${app.pivot.max-result-rows:10000}")
    private int maxResultRows;

    public PivotService(DatasetRepository datasetRepository,
                        DatasetColumnRepository columnRepository,
                        JdbcTemplate jdbcTemplate,
                        PivotSqlBuilder sqlBuilder) {
        this.datasetRepository = datasetRepository;
        this.columnRepository = columnRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.sqlBuilder = sqlBuilder;
    }

    public PivotResultDto execute(PivotExecuteRequestDto request) {
        Dataset dataset = datasetRepository.findById(request.datasetId())
                .orElseThrow(() -> new NoSuchElementException("Dataset not found: " + request.datasetId()));

        List<DatasetColumn> columns = columnRepository.findByDatasetIdOrderByOrdinal(dataset.getId());
        Set<String> validColumns = columns.stream()
                .map(DatasetColumn::getColumnName)
                .collect(Collectors.toSet());

        PivotConfigDto config = request.config();

        // Reject RAW aggregation for non-numeric fields
        Map<String, FieldType> columnTypes = columns.stream()
                .collect(Collectors.toMap(DatasetColumn::getColumnName, DatasetColumn::getFieldType));
        for (var vf : config.values()) {
            if (vf.aggregation() == AggregationType.RAW) {
                FieldType ft = columnTypes.get(vf.fieldId());
                if (ft != null && ft != FieldType.NUMBER) {
                    throw new IllegalArgumentException(
                            "RAW aggregation is only allowed for numeric fields; use COUNT for field '" + vf.name() + "'");
                }
            }
        }
        int offset = Math.max(0, request.offset() != null ? request.offset() : 0);
        int limit = Math.max(1, Math.min(request.limit() != null ? request.limit() : maxResultRows, maxResultRows));

        var countQuery = sqlBuilder.buildCountQuery(config, dataset.getTableName(), validColumns);
        Long totalRows = jdbcTemplate.queryForObject(countQuery.sql(), Long.class, countQuery.params().toArray());

        var mainQuery = sqlBuilder.buildPivotQuery(config, dataset.getTableName(), validColumns, offset, limit);
        List<Map<String, Object>> rawRows = jdbcTemplate.queryForList(mainQuery.sql(), mainQuery.params().toArray());

        var totalsQuery = sqlBuilder.buildTotalsQuery(config, dataset.getTableName(), validColumns);
        List<Map<String, Object>> totalsRaw = jdbcTemplate.queryForList(totalsQuery.sql(), totalsQuery.params().toArray());

        return transformResult(rawRows, totalsRaw, config, totalRows != null ? totalRows : 0, offset, limit);
    }

    public PivotResultDto executeExternal(ExternalPivotRequestDto request, ConnectionService connectionService, String userEmail) {
        JdbcTemplate extJdbc = connectionService.getJdbc(request.connectionId(), userEmail);
        List<TableFieldDto> fields = connectionService.getTableFields(request.connectionId(), request.schema(), request.tableName(), userEmail);
        Set<String> validColumns = fields.stream().map(TableFieldDto::name).collect(Collectors.toSet());

        PivotConfigDto config = request.config();
        String tableName = sqlBuilder.qualifiedTable(request.schema(), request.tableName());

        int offset = Math.max(0, request.offset() != null ? request.offset() : 0);
        int limit = Math.max(1, Math.min(request.limit() != null ? request.limit() : maxResultRows, maxResultRows));

        var countQuery = sqlBuilder.buildCountQuery(config, tableName, validColumns);
        Long totalRows = extJdbc.queryForObject(countQuery.sql(), Long.class, countQuery.params().toArray());

        var mainQuery = sqlBuilder.buildPivotQuery(config, tableName, validColumns, offset, limit);
        List<Map<String, Object>> rawRows = extJdbc.queryForList(mainQuery.sql(), mainQuery.params().toArray());

        var totalsQuery = sqlBuilder.buildTotalsQuery(config, tableName, validColumns);
        List<Map<String, Object>> totalsRaw = extJdbc.queryForList(totalsQuery.sql(), totalsQuery.params().toArray());

        return transformResult(rawRows, totalsRaw, config, totalRows != null ? totalRows : 0, offset, limit);
    }

    public String previewExternalSql(ExternalPivotRequestDto request, ConnectionService connectionService, String userEmail) {
        connectionService.getJdbc(request.connectionId(), userEmail);
        String tableName = sqlBuilder.qualifiedTable(request.schema(), request.tableName());
        return sqlBuilder.buildPreviewSql(request.config(), tableName);
    }

    public String previewSql(PivotExecuteRequestDto request) {
        Dataset dataset = datasetRepository.findById(request.datasetId())
                .orElseThrow(() -> new NoSuchElementException("Dataset not found: " + request.datasetId()));
        return sqlBuilder.buildPreviewSql(request.config(), dataset.getTableName());
    }

    private PivotResultDto transformResult(List<Map<String, Object>> rawRows,
                                           List<Map<String, Object>> totalsRaw,
                                           PivotConfigDto config,
                                           long totalRows, int offset, int limit) {
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

            Map<String, Double> values = buildValueMap(entry.getValue(), colKeys, colFieldIds, config.values());
            resultRows.add(new PivotResultRowDto(keys, values, null));
        }

        resultRows.sort(Comparator.comparing(r -> r.keys().isEmpty() ? "" : r.keys().getFirst()));

        Map<String, Double> totals = buildTotalsMap(totalsRaw, colFieldIds, config.values());

        List<List<String>> columnHeaders = colKeys.stream()
                .map(k -> k.isEmpty() ? List.<String>of() : List.of(k.split(" / ", -1)))
                .toList();

        return new PivotResultDto(columnHeaders, resultRows, totals, totalRows, offset, limit);
    }

    private Map<String, Double> buildValueMap(List<Map<String, Object>> groupRows,
                                              List<String> colKeys,
                                              List<String> colFieldIds,
                                              List<PivotValueFieldDto> valueFields) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (var row : groupRows) {
            String colKey = colFieldIds.isEmpty() ? "" : buildColKey(row, colFieldIds);
            for (var vf : valueFields) {
                String aggKey = vf.fieldId() + "_" + vf.aggregation().getValue();
                String valueName = vf.name() + " (" + vf.aggregation().getValue() + ")";
                String label;
                if (colKey.isEmpty()) {
                    label = valueName;
                } else if (valueFields.size() == 1) {
                    label = colKey; // single metric — just show column value
                } else {
                    label = colKey + " | " + valueName;
                }
                values.put(label, toDouble(row.get(aggKey)));
            }
        }
        return values;
    }

    private Map<String, Double> buildTotalsMap(List<Map<String, Object>> totalsRaw,
                                               List<String> colFieldIds,
                                               List<PivotValueFieldDto> valueFields) {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (var row : totalsRaw) {
            String colKey = colFieldIds.isEmpty() ? "" : buildColKey(row, colFieldIds);
            for (var vf : valueFields) {
                String aggKey = vf.fieldId() + "_" + vf.aggregation().getValue();
                String valueName = vf.name() + " (" + vf.aggregation().getValue() + ")";
                String label;
                if (colKey.isEmpty()) {
                    label = valueName;
                } else if (valueFields.size() == 1) {
                    label = colKey;
                } else {
                    label = colKey + " | " + valueName;
                }
                totals.put(label, toDouble(row.get(aggKey)));
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

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return Math.round(n.doubleValue() * 100.0) / 100.0;
        try { return Double.parseDouble(val.toString()); } catch (NumberFormatException e) { return 0.0; }
    }
}
