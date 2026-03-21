package com.prosto.analytics.service;

import com.prosto.analytics.dto.*;
import com.prosto.analytics.model.AggregationType;
import com.prosto.analytics.model.FilterOperator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PivotSqlBuilder {

    public record SqlQuery(String sql, List<Object> params) {}

    public SqlQuery buildPivotQuery(PivotConfigDto config, String tableName, Set<String> validColumns) {
        validate(config, validColumns);

        var sql = new StringBuilder();
        var params = new ArrayList<>();
        var selectParts = new ArrayList<String>();
        var groupByParts = new ArrayList<String>();

        for (var f : config.rows()) {
            selectParts.add(ident(f.fieldId()));
            groupByParts.add(ident(f.fieldId()));
        }
        for (var f : config.columns()) {
            selectParts.add(ident(f.fieldId()));
            groupByParts.add(ident(f.fieldId()));
        }
        for (var f : config.values()) {
            if (f.aggregation() == AggregationType.RAW) {
                String alias = f.fieldId() + "_raw";
                selectParts.add("MIN(" + ident(f.fieldId()) + ") AS " + ident(alias));
            } else {
                String agg = f.aggregation().toSql();
                String alias = f.fieldId() + "_" + f.aggregation().getValue();
                selectParts.add(agg + "(" + ident(f.fieldId()) + ") AS " + ident(alias));
            }
        }

        sql.append("SELECT ").append(String.join(", ", selectParts));
        sql.append(" FROM ").append(tableRef(tableName));

        appendWhere(sql, params, config.filters(), validColumns);

        if (!groupByParts.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupByParts));
            sql.append(" ORDER BY ").append(String.join(", ", groupByParts));
        }

        return new SqlQuery(sql.toString(), params);
    }

    public SqlQuery buildPivotQuery(PivotConfigDto config, String tableName, Set<String> validColumns, int offset, int limit) {
        var query = buildPivotQuery(config, tableName, validColumns);
        var sql = new StringBuilder(query.sql());
        var params = new ArrayList<>(query.params());
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return new SqlQuery(sql.toString(), params);
    }

    public SqlQuery buildCountQuery(PivotConfigDto config, String tableName, Set<String> validColumns) {
        validate(config, validColumns);

        var groupByParts = new ArrayList<String>();
        for (var f : config.rows()) groupByParts.add(ident(f.fieldId()));
        for (var f : config.columns()) groupByParts.add(ident(f.fieldId()));

        var params = new ArrayList<>();

        if (groupByParts.isEmpty()) {
            // Aggregate-only pivot (no row/column dimensions) always produces exactly 1 result row
            return new SqlQuery("SELECT 1 AS cnt", params);
        }

        var inner = new StringBuilder("SELECT 1 FROM " + tableRef(tableName));
        appendWhere(inner, params, config.filters(), validColumns);
        inner.append(" GROUP BY ").append(String.join(", ", groupByParts));

        var sql = "SELECT COUNT(*) AS cnt FROM (" + inner + ") sub";
        return new SqlQuery(sql, params);
    }

    public SqlQuery buildTotalsQuery(PivotConfigDto config, String tableName, Set<String> validColumns) {
        validate(config, validColumns);

        var sql = new StringBuilder();
        var params = new ArrayList<>();
        var selectParts = new ArrayList<String>();
        var groupByParts = new ArrayList<String>();

        for (var f : config.columns()) {
            selectParts.add(ident(f.fieldId()));
            groupByParts.add(ident(f.fieldId()));
        }
        for (var f : config.values()) {
            if (f.aggregation() == AggregationType.RAW) {
                // For totals with RAW, use MIN as fallback (no meaningful total for raw)
                String alias = f.fieldId() + "_raw";
                selectParts.add("MIN(" + ident(f.fieldId()) + ") AS " + ident(alias));
            } else {
                String agg = f.aggregation().toSql();
                String alias = f.fieldId() + "_" + f.aggregation().getValue();
                selectParts.add(agg + "(" + ident(f.fieldId()) + ") AS " + ident(alias));
            }
        }

        sql.append("SELECT ").append(String.join(", ", selectParts));
        sql.append(" FROM ").append(tableRef(tableName));

        appendWhere(sql, params, config.filters(), validColumns);

        if (!groupByParts.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupByParts));
        }

        return new SqlQuery(sql.toString(), params);
    }

    public String buildPreviewSql(PivotConfigDto config, String tableName) {
        var selectParts = new ArrayList<String>();
        var groupByParts = new ArrayList<String>();

        for (var f : config.rows()) {
            selectParts.add("  " + f.name());
            groupByParts.add(f.name());
        }
        for (var f : config.columns()) {
            selectParts.add("  " + f.name());
            groupByParts.add(f.name());
        }
        for (var f : config.values()) {
            if (f.aggregation() == AggregationType.RAW) {
                selectParts.add("  MIN(" + f.name() + ") AS \"" + f.name() + "\"");
            } else {
                String agg = f.aggregation().toSql();
                selectParts.add("  " + agg + "(" + f.name() + ") AS \"" +
                        f.name() + " (" + f.aggregation().getValue() + ")\"");
            }
        }

        var sql = new StringBuilder();
        sql.append("SELECT\n").append(String.join(",\n", selectParts));
        sql.append("\nFROM ").append(tableName);

        if (config.filters() != null && !config.filters().isEmpty()) {
            var whereParts = new ArrayList<String>();
            for (var f : config.filters()) {
                if (f.filterValue() == null || f.filterValue().isEmpty()) continue;
                switch (f.operator()) {
                    case EQ -> whereParts.add(f.name() + " = '" + f.filterValue().getFirst() + "'");
                    case NEQ -> whereParts.add(f.name() + " != '" + f.filterValue().getFirst() + "'");
                    case GT -> whereParts.add(f.name() + " > " + f.filterValue().getFirst());
                    case LT -> whereParts.add(f.name() + " < " + f.filterValue().getFirst());
                    case IN -> {
                        String vals = f.filterValue().stream()
                                .map(v -> "'" + v + "'")
                                .collect(Collectors.joining(", "));
                        whereParts.add(f.name() + " IN (" + vals + ")");
                    }
                }
            }
            if (!whereParts.isEmpty()) {
                sql.append("\nWHERE ").append(String.join("\n  AND ", whereParts));
            }
        }

        if (!groupByParts.isEmpty()) {
            sql.append("\nGROUP BY ").append(String.join(", ", groupByParts));
            sql.append("\nORDER BY ").append(groupByParts.getFirst());
        }

        return sql.toString();
    }

    private void appendWhere(StringBuilder sql, List<Object> params,
                             List<PivotFilterFieldDto> filters, Set<String> validColumns) {
        if (filters == null || filters.isEmpty()) return;

        var whereParts = new ArrayList<String>();
        for (var f : filters) {
            if (f.filterValue() == null || f.filterValue().isEmpty()) continue;
            validateColumn(f.fieldId(), validColumns);

            switch (f.operator()) {
                case EQ -> {
                    whereParts.add(ident(f.fieldId()) + " = ?::text");
                    params.add(f.filterValue().getFirst());
                }
                case NEQ -> {
                    whereParts.add(ident(f.fieldId()) + " != ?::text");
                    params.add(f.filterValue().getFirst());
                }
                case GT -> {
                    whereParts.add(ident(f.fieldId()) + " > ?::numeric");
                    params.add(f.filterValue().getFirst());
                }
                case LT -> {
                    whereParts.add(ident(f.fieldId()) + " < ?::numeric");
                    params.add(f.filterValue().getFirst());
                }
                case IN -> {
                    String placeholders = f.filterValue().stream()
                            .map(v -> "?::text")
                            .collect(Collectors.joining(", "));
                    whereParts.add(ident(f.fieldId()) + " IN (" + placeholders + ")");
                    params.addAll(f.filterValue());
                }
            }
        }

        if (!whereParts.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereParts));
        }
    }

    private void validate(PivotConfigDto config, Set<String> validColumns) {
        for (var f : config.rows()) validateColumn(f.fieldId(), validColumns);
        for (var f : config.columns()) validateColumn(f.fieldId(), validColumns);
        for (var f : config.values()) validateColumn(f.fieldId(), validColumns);
    }

    private void validateColumn(String columnName, Set<String> validColumns) {
        if (!validColumns.contains(columnName)) {
            throw new IllegalArgumentException("Invalid column: " + columnName);
        }
    }

    public String qualifiedTable(String schema, String table) {
        return ident(schema) + "." + ident(table);
    }

    private String tableRef(String tableName) {
        // If already schema-qualified (contains a dot between quoted identifiers), use as-is
        if (tableName.contains(".") && tableName.startsWith("\"")) {
            return tableName;
        }
        return ident(tableName);
    }

    private String ident(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
