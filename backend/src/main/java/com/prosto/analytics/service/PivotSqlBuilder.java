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

    // --- Backward-compatible overloads (default POSTGRESQL dialect) ---

    public SqlQuery buildPivotQuery(PivotConfigDto config, String tableName, Set<String> validColumns) {
        return buildPivotQuery(config, tableName, validColumns, SqlDialect.POSTGRESQL);
    }

    public SqlQuery buildPivotQuery(PivotConfigDto config, String tableName, Set<String> validColumns, int offset, int limit) {
        return buildPivotQuery(config, tableName, validColumns, offset, limit, SqlDialect.POSTGRESQL);
    }

    public SqlQuery buildCountQuery(PivotConfigDto config, String tableName, Set<String> validColumns) {
        return buildCountQuery(config, tableName, validColumns, SqlDialect.POSTGRESQL);
    }

    public SqlQuery buildTotalsQuery(PivotConfigDto config, String tableName, Set<String> validColumns) {
        return buildTotalsQuery(config, tableName, validColumns, SqlDialect.POSTGRESQL);
    }

    // --- Dialect-aware methods ---

    public SqlQuery buildPivotQuery(PivotConfigDto config, String tableName, Set<String> validColumns, SqlDialect dialect) {
        validate(config, validColumns);

        boolean allOriginal = isAllOriginal(config);
        boolean duckdb = dialect == SqlDialect.DUCKDB;

        var sql = new StringBuilder();
        var params = new ArrayList<>();
        var selectParts = new ArrayList<String>();
        var rowGroupBy = new ArrayList<String>();
        var colGroupBy = new ArrayList<String>();

        for (var f : config.rows()) {
            selectParts.add(ident(f.fieldId()));
            rowGroupBy.add(ident(f.fieldId()));
        }
        for (var f : config.columns()) {
            selectParts.add(ident(f.fieldId()));
            colGroupBy.add(ident(f.fieldId()));
        }

        var groupByParts = new ArrayList<>(rowGroupBy);
        groupByParts.addAll(colGroupBy);

        for (var f : config.values()) {
            String alias = f.fieldId() + "_" + f.aggregation().getValue();
            String col = ident(f.fieldId());
            String expr;
            if (f.aggregation() == AggregationType.ORIGINAL && !allOriginal) {
                expr = duckdb ? "FIRST(" + col + ")" : "(ARRAY_AGG(" + col + "))[1]";
            } else if (f.aggregation() == AggregationType.ORIGINAL) {
                expr = col;
            } else if (!allOriginal && f.aggregation().isWindowFunction()) {
                expr = buildWindowExpression(f.aggregation(), col, rowGroupBy, colGroupBy);
            } else {
                expr = f.aggregation().toSqlExpression(col, duckdb);
            }
            selectParts.add(expr + " AS " + ident(alias));
        }

        sql.append("SELECT ").append(String.join(", ", selectParts));
        sql.append(" FROM ").append(tableRef(tableName));

        appendWhere(sql, params, config.filters(), validColumns, dialect);

        if (allOriginal) {
            if (!groupByParts.isEmpty()) {
                sql.append(" ORDER BY ").append(String.join(", ", groupByParts));
            }
        } else if (!groupByParts.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupByParts));
            sql.append(" ORDER BY ").append(String.join(", ", groupByParts));
        }

        return new SqlQuery(sql.toString(), params);
    }

    public SqlQuery buildPivotQuery(PivotConfigDto config, String tableName, Set<String> validColumns, int offset, int limit, SqlDialect dialect) {
        var query = buildPivotQuery(config, tableName, validColumns, dialect);
        var sql = new StringBuilder(query.sql());
        var params = new ArrayList<>(query.params());
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return new SqlQuery(sql.toString(), params);
    }

    public SqlQuery buildCountQuery(PivotConfigDto config, String tableName, Set<String> validColumns, SqlDialect dialect) {
        validate(config, validColumns);

        var params = new ArrayList<>();

        if (isAllOriginal(config)) {
            var sql = new StringBuilder("SELECT COUNT(*) AS cnt FROM " + tableRef(tableName));
            appendWhere(sql, params, config.filters(), validColumns, dialect);
            return new SqlQuery(sql.toString(), params);
        }

        var groupByParts = new ArrayList<String>();
        for (var f : config.rows()) groupByParts.add(ident(f.fieldId()));
        for (var f : config.columns()) groupByParts.add(ident(f.fieldId()));

        if (groupByParts.isEmpty()) {
            return new SqlQuery("SELECT 1 AS cnt", params);
        }

        var inner = new StringBuilder("SELECT 1 FROM " + tableRef(tableName));
        appendWhere(inner, params, config.filters(), validColumns, dialect);
        inner.append(" GROUP BY ").append(String.join(", ", groupByParts));

        var sql = "SELECT COUNT(*) AS cnt FROM (" + inner + ") sub";
        return new SqlQuery(sql, params);
    }

    public SqlQuery buildTotalsQuery(PivotConfigDto config, String tableName, Set<String> validColumns, SqlDialect dialect) {
        validate(config, validColumns);

        if (isAllOriginal(config)) {
            return new SqlQuery("SELECT 1 WHERE FALSE", new ArrayList<>());
        }

        boolean duckdb = dialect == SqlDialect.DUCKDB;

        var sql = new StringBuilder();
        var params = new ArrayList<>();
        var selectParts = new ArrayList<String>();
        var groupByParts = new ArrayList<String>();

        for (var f : config.columns()) {
            selectParts.add(ident(f.fieldId()));
            groupByParts.add(ident(f.fieldId()));
        }
        for (var f : config.values()) {
            String alias = f.fieldId() + "_" + f.aggregation().getValue();
            String col = ident(f.fieldId());
            String expr;
            if (f.aggregation() == AggregationType.ORIGINAL) {
                expr = duckdb ? "FIRST(" + col + ")" : "(ARRAY_AGG(" + col + "))[1]";
            } else {
                expr = f.aggregation().baseSqlExpression(col, duckdb);
            }
            selectParts.add(expr + " AS " + ident(alias));
        }

        sql.append("SELECT ").append(String.join(", ", selectParts));
        sql.append(" FROM ").append(tableRef(tableName));

        appendWhere(sql, params, config.filters(), validColumns, dialect);

        if (!groupByParts.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupByParts));
        }

        return new SqlQuery(sql.toString(), params);
    }

    /** Display-only SQL for UI preview — never executed against database. */
    public String buildPreviewSql(PivotConfigDto config, String tableName) {
        boolean allOriginal = isAllOriginal(config);

        var selectParts = new ArrayList<String>();
        var groupByParts = new ArrayList<String>();
        var rowNames = new ArrayList<String>();
        var colNames = new ArrayList<String>();

        for (var f : config.rows()) {
            selectParts.add("  " + f.name());
            groupByParts.add(f.name());
            rowNames.add(f.name());
        }
        for (var f : config.columns()) {
            selectParts.add("  " + f.name());
            groupByParts.add(f.name());
            colNames.add(f.name());
        }
        for (var f : config.values()) {
            if (f.aggregation() == AggregationType.ORIGINAL && allOriginal) {
                selectParts.add("  " + f.name());
            } else if (f.aggregation() == AggregationType.ORIGINAL) {
                selectParts.add("  (ARRAY_AGG(" + f.name() + "))[1] AS \"" + f.name() + "\"");
            } else {
                String aggLabel = f.aggregation().getDisplayLabel();
                if (f.aggregation().isWindowFunction()) {
                    String baseExpr = buildPreviewWindowExpression(f.aggregation(), f.name(), rowNames, colNames);
                    selectParts.add("  " + baseExpr + " AS \"" + f.name() + " (" + aggLabel + ")\"");
                } else {
                    String expr = f.aggregation().toSqlExpression(f.name());
                    selectParts.add("  " + expr + " AS \"" + f.name() + " (" + aggLabel + ")\"");
                }
            }
        }

        var sql = new StringBuilder();
        sql.append("SELECT\n").append(String.join(",\n", selectParts));
        sql.append("\nFROM ").append(tableName);

        if (config.filters() != null && !config.filters().isEmpty()) {
            var whereParts = new ArrayList<String>();
            for (var f : config.filters()) {
                if (f.filterValue() == null || f.filterValue().isEmpty()) continue;
                String col = ident(f.fieldId());
                switch (f.operator()) {
                    case EQ -> whereParts.add(col + " = '" + escapeSql(f.filterValue().getFirst()) + "'");
                    case NEQ -> whereParts.add(col + " != '" + escapeSql(f.filterValue().getFirst()) + "'");
                    case GT -> whereParts.add(col + " > '" + escapeSql(f.filterValue().getFirst()) + "'");
                    case GTE -> whereParts.add(col + " >= '" + escapeSql(f.filterValue().getFirst()) + "'");
                    case LT -> whereParts.add(col + " < '" + escapeSql(f.filterValue().getFirst()) + "'");
                    case LTE -> whereParts.add(col + " <= '" + escapeSql(f.filterValue().getFirst()) + "'");
                    case IN -> {
                        String vals = f.filterValue().stream()
                                .map(v -> "'" + escapeSql(v) + "'")
                                .collect(Collectors.joining(", "));
                        whereParts.add(col + " IN (" + vals + ")");
                    }
                }
            }
            if (!whereParts.isEmpty()) {
                sql.append("\nWHERE ").append(String.join("\n  AND ", whereParts));
            }
        }

        if (allOriginal) {
            if (!groupByParts.isEmpty()) {
                sql.append("\nORDER BY ").append(groupByParts.getFirst());
            }
        } else if (!groupByParts.isEmpty()) {
            sql.append("\nGROUP BY ").append(String.join(", ", groupByParts));
            sql.append("\nORDER BY ").append(groupByParts.getFirst());
        }

        return sql.toString();
    }

    private String buildWindowExpression(AggregationType agg, String col,
                                         List<String> rowGroupBy, List<String> colGroupBy) {
        String baseAgg = agg.baseSqlExpression(col);
        String baseFunc = baseAgg.startsWith("COUNT") ? "COUNT" : "SUM";

        return switch (agg) {
            case SUM_PCT_TOTAL, COUNT_PCT_TOTAL ->
                    baseAgg + " * 100.0 / NULLIF(SUM(" + baseFunc + "(" + col + ")) OVER (), 0)";
            case SUM_PCT_ROW, COUNT_PCT_ROW -> {
                String partition = rowGroupBy.isEmpty() ? "" : "PARTITION BY " + String.join(", ", rowGroupBy);
                yield baseAgg + " * 100.0 / NULLIF(SUM(" + baseFunc + "(" + col + ")) OVER (" + partition + "), 0)";
            }
            case SUM_PCT_COL, COUNT_PCT_COL -> {
                String partition = colGroupBy.isEmpty() ? "" : "PARTITION BY " + String.join(", ", colGroupBy);
                yield baseAgg + " * 100.0 / NULLIF(SUM(" + baseFunc + "(" + col + ")) OVER (" + partition + "), 0)";
            }
            case RUNNING_SUM -> {
                String orderBy = rowGroupBy.isEmpty()
                        ? (colGroupBy.isEmpty() ? "" : "ORDER BY " + String.join(", ", colGroupBy))
                        : "ORDER BY " + String.join(", ", rowGroupBy);
                yield "SUM(SUM(" + col + ")) OVER (" + orderBy + " ROWS UNBOUNDED PRECEDING)";
            }
            default -> throw new IllegalArgumentException("Not a window function type: " + agg);
        };
    }

    private String buildPreviewWindowExpression(AggregationType agg, String colName,
                                                 List<String> rowNames, List<String> colNames) {
        String baseFunc = switch (agg) {
            case COUNT_PCT_TOTAL, COUNT_PCT_ROW, COUNT_PCT_COL -> "COUNT";
            default -> "SUM";
        };

        return switch (agg) {
            case SUM_PCT_TOTAL, COUNT_PCT_TOTAL ->
                    baseFunc + "(" + colName + ") * 100.0 / NULLIF(SUM(" + baseFunc + "(" + colName + ")) OVER (), 0)";
            case SUM_PCT_ROW, COUNT_PCT_ROW -> {
                String partition = rowNames.isEmpty() ? "" : "PARTITION BY " + String.join(", ", rowNames);
                yield baseFunc + "(" + colName + ") * 100.0 / NULLIF(SUM(" + baseFunc + "(" + colName + ")) OVER (" + partition + "), 0)";
            }
            case SUM_PCT_COL, COUNT_PCT_COL -> {
                String partition = colNames.isEmpty() ? "" : "PARTITION BY " + String.join(", ", colNames);
                yield baseFunc + "(" + colName + ") * 100.0 / NULLIF(SUM(" + baseFunc + "(" + colName + ")) OVER (" + partition + "), 0)";
            }
            case RUNNING_SUM -> {
                String orderBy = rowNames.isEmpty()
                        ? (colNames.isEmpty() ? "" : "ORDER BY " + String.join(", ", colNames))
                        : "ORDER BY " + String.join(", ", rowNames);
                yield "SUM(SUM(" + colName + ")) OVER (" + orderBy + " ROWS UNBOUNDED PRECEDING)";
            }
            default -> throw new IllegalArgumentException("Not a window function type: " + agg);
        };
    }

    private void appendWhere(StringBuilder sql, List<Object> params,
                             List<PivotFilterFieldDto> filters, Set<String> validColumns,
                             SqlDialect dialect) {
        if (filters == null || filters.isEmpty()) return;

        var whereParts = new ArrayList<String>();
        for (var f : filters) {
            if (f.filterValue() == null || f.filterValue().isEmpty()) continue;
            validateColumn(f.fieldId(), validColumns);

            switch (f.operator()) {
                case EQ -> {
                    whereParts.add(ident(f.fieldId()) + " = " + dialect.castText("?"));
                    params.add(f.filterValue().getFirst());
                }
                case NEQ -> {
                    whereParts.add(ident(f.fieldId()) + " != " + dialect.castText("?"));
                    params.add(f.filterValue().getFirst());
                }
                case GT -> {
                    whereParts.add("CAST(" + ident(f.fieldId()) + " AS text) > " + dialect.castText("?"));
                    params.add(f.filterValue().getFirst());
                }
                case GTE -> {
                    whereParts.add("CAST(" + ident(f.fieldId()) + " AS text) >= " + dialect.castText("?"));
                    params.add(f.filterValue().getFirst());
                }
                case LT -> {
                    whereParts.add("CAST(" + ident(f.fieldId()) + " AS text) < " + dialect.castText("?"));
                    params.add(f.filterValue().getFirst());
                }
                case LTE -> {
                    whereParts.add("CAST(" + ident(f.fieldId()) + " AS text) <= " + dialect.castText("?"));
                    params.add(f.filterValue().getFirst());
                }
                case IN -> {
                    String placeholders = f.filterValue().stream()
                            .map(v -> dialect.castText("?"))
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

    private void appendWhere(StringBuilder sql, List<Object> params,
                             List<PivotFilterFieldDto> filters, Set<String> validColumns) {
        appendWhere(sql, params, filters, validColumns, SqlDialect.POSTGRESQL);
    }

    private boolean isAllOriginal(PivotConfigDto config) {
        return config.values().stream()
                .allMatch(v -> v.aggregation() == AggregationType.ORIGINAL);
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
        if (tableName.contains(".") && tableName.startsWith("\"")) {
            return tableName;
        }
        return ident(tableName);
    }

    private String escapeSql(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private String ident(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
