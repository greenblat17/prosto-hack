package com.prosto.analytics.service;

import com.prosto.analytics.dto.*;
import com.prosto.analytics.model.AggregationType;
import com.prosto.analytics.model.FilterOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class PivotSqlBuilderTest {

    private PivotSqlBuilder builder;

    private static final Set<String> VALID = Set.of("city", "revenue", "quantity", "date", "category");

    @BeforeEach
    void setUp() {
        builder = new PivotSqlBuilder();
    }

    private static PivotFieldDto field(String id) {
        return new PivotFieldDto(id, id);
    }

    private static PivotValueFieldDto value(String id, AggregationType agg) {
        return new PivotValueFieldDto(id, id, agg);
    }

    private static PivotFilterFieldDto filter(String id, FilterOperator op, String... values) {
        return new PivotFilterFieldDto(id, id, op, List.of(values));
    }

    private static PivotConfigDto config(List<PivotFieldDto> rows, List<PivotFieldDto> cols,
                                          List<PivotValueFieldDto> vals, List<PivotFilterFieldDto> filters) {
        return new PivotConfigDto(rows, cols, vals, filters);
    }

    @Nested
    @DisplayName("buildPivotQuery")
    class PivotQuery {

        @Test
        @DisplayName("simple GROUP BY with SUM")
        void simpleGroupBySum() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(value("revenue", AggregationType.SUM)),
                    List.of()
            );

            var result = builder.buildPivotQuery(cfg, "sales", VALID);

            assertThat(result.sql())
                    .contains("SELECT")
                    .contains("\"city\"")
                    .contains("SUM(\"revenue\")")
                    .contains("GROUP BY")
                    .contains("ORDER BY");
            assertThat(result.params()).isEmpty();
        }

        @Test
        @DisplayName("ORIGINAL without GROUP BY — no aggregation")
        void originalNoGroupBy() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(value("revenue", AggregationType.ORIGINAL)),
                    List.of()
            );

            var result = builder.buildPivotQuery(cfg, "sales", VALID);

            assertThat(result.sql())
                    .doesNotContain("GROUP BY")
                    .contains("ORDER BY");
        }

        @Test
        @DisplayName("ORIGINAL mixed with SUM — wraps ORIGINAL in FIRST()")
        void originalMixedWithAggregate() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(
                            value("revenue", AggregationType.SUM),
                            value("quantity", AggregationType.ORIGINAL)
                    ),
                    List.of()
            );

            var result = builder.buildPivotQuery(cfg, "sales", VALID);

            assertThat(result.sql())
                    .contains("GROUP BY")
                    .contains("(ARRAY_AGG(\"quantity\"))[1]");
        }

        @Test
        @DisplayName("ORIGINAL mixed with SUM — DuckDB uses FIRST()")
        void originalMixedDuckdb() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(
                            value("revenue", AggregationType.SUM),
                            value("quantity", AggregationType.ORIGINAL)
                    ),
                    List.of()
            );

            var result = builder.buildPivotQuery(cfg, "sales", VALID, SqlDialect.DUCKDB);

            assertThat(result.sql()).contains("FIRST(\"quantity\")");
        }

        @Test
        @DisplayName("with LIMIT and OFFSET")
        void withPagination() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(value("revenue", AggregationType.COUNT)),
                    List.of()
            );

            var result = builder.buildPivotQuery(cfg, "sales", VALID, 10, 50);

            assertThat(result.sql()).endsWith("LIMIT ? OFFSET ?");
            assertThat(result.params()).containsExactly(50, 10);
        }

        @Test
        @DisplayName("rows + columns produce combined GROUP BY")
        void rowsAndColumns() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(field("category")),
                    List.of(value("revenue", AggregationType.SUM)),
                    List.of()
            );

            var result = builder.buildPivotQuery(cfg, "sales", VALID);

            assertThat(result.sql())
                    .contains("\"city\"")
                    .contains("\"category\"")
                    .contains("GROUP BY \"city\", \"category\"");
        }
    }

    @Nested
    @DisplayName("Filters")
    class Filters {

        @Test
        @DisplayName("EQ filter adds WHERE with param")
        void eqFilter() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(value("revenue", AggregationType.SUM)),
                    List.of(filter("city", FilterOperator.EQ, "Москва"))
            );

            var result = builder.buildPivotQuery(cfg, "sales", VALID);

            assertThat(result.sql()).contains("WHERE").contains("\"city\" = ?::text");
            assertThat(result.params()).containsExactly("Москва");
        }

        @Test
        @DisplayName("GTE/LTE filters use CAST for date range")
        void gteAndLteFilters() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(value("revenue", AggregationType.SUM)),
                    List.of(
                            filter("date", FilterOperator.GTE, "2025-01-01"),
                            filter("date", FilterOperator.LTE, "2025-03-31")
                    )
            );

            var result = builder.buildPivotQuery(cfg, "sales", VALID);

            assertThat(result.sql())
                    .contains("CAST(\"date\" AS text) >= ?::text")
                    .contains("CAST(\"date\" AS text) <= ?::text");
            assertThat(result.params()).containsExactly("2025-01-01", "2025-03-31");
        }

        @Test
        @DisplayName("IN filter produces multiple placeholders")
        void inFilter() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(value("revenue", AggregationType.SUM)),
                    List.of(filter("city", FilterOperator.IN, "Москва", "Питер"))
            );

            var result = builder.buildPivotQuery(cfg, "sales", VALID);

            assertThat(result.sql()).contains("\"city\" IN (?::text, ?::text)");
            assertThat(result.params()).containsExactly("Москва", "Питер");
        }

        @Test
        @DisplayName("DuckDB dialect uses CAST instead of ::text")
        void duckdbDialect() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(value("revenue", AggregationType.SUM)),
                    List.of(filter("city", FilterOperator.EQ, "Москва"))
            );

            var result = builder.buildPivotQuery(cfg, "sales", VALID, SqlDialect.DUCKDB);

            assertThat(result.sql()).contains("CAST(? AS VARCHAR)");
            assertThat(result.sql()).doesNotContain("::text");
        }

        @Test
        @DisplayName("empty filterValue is ignored")
        void emptyFilterIgnored() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(value("revenue", AggregationType.SUM)),
                    List.of(new PivotFilterFieldDto("city", "city", FilterOperator.EQ, List.of()))
            );

            var result = builder.buildPivotQuery(cfg, "sales", VALID);

            assertThat(result.sql()).doesNotContain("WHERE");
        }

        @Test
        @DisplayName("null filters list is safe")
        void nullFilters() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(value("revenue", AggregationType.SUM)),
                    null
            );

            var result = builder.buildPivotQuery(cfg, "sales", VALID);

            assertThat(result.sql()).doesNotContain("WHERE");
        }
    }

    @Nested
    @DisplayName("buildCountQuery")
    class CountQuery {

        @Test
        @DisplayName("allOriginal — simple COUNT(*)")
        void originalCount() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(value("revenue", AggregationType.ORIGINAL)),
                    List.of()
            );

            var result = builder.buildCountQuery(cfg, "sales", VALID);

            assertThat(result.sql()).startsWith("SELECT COUNT(*) AS cnt FROM");
        }

        @Test
        @DisplayName("with aggregation — wraps in subquery")
        void aggregatedCount() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(value("revenue", AggregationType.SUM)),
                    List.of()
            );

            var result = builder.buildCountQuery(cfg, "sales", VALID);

            assertThat(result.sql())
                    .contains("SELECT COUNT(*) AS cnt FROM (")
                    .contains("GROUP BY")
                    .contains(") sub");
        }

        @Test
        @DisplayName("no groupBy — returns SELECT 1")
        void noGroupBy() {
            var cfg = config(
                    List.of(),
                    List.of(),
                    List.of(value("revenue", AggregationType.SUM)),
                    List.of()
            );

            var result = builder.buildCountQuery(cfg, "sales", VALID);

            assertThat(result.sql()).isEqualTo("SELECT 1 AS cnt");
        }
    }

    @Nested
    @DisplayName("buildTotalsQuery")
    class TotalsQuery {

        @Test
        @DisplayName("allOriginal — returns empty")
        void originalTotals() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(value("revenue", AggregationType.ORIGINAL)),
                    List.of()
            );

            var result = builder.buildTotalsQuery(cfg, "sales", VALID);

            assertThat(result.sql()).contains("WHERE FALSE");
        }

        @Test
        @DisplayName("aggregation — sums across all rows")
        void aggregatedTotals() {
            var cfg = config(
                    List.of(field("city")),
                    List.of(),
                    List.of(value("revenue", AggregationType.SUM)),
                    List.of()
            );

            var result = builder.buildTotalsQuery(cfg, "sales", VALID);

            assertThat(result.sql())
                    .contains("SUM(\"revenue\")")
                    .doesNotContain("GROUP BY");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("invalid column throws")
        void invalidColumn() {
            var cfg = config(
                    List.of(field("nonexistent")),
                    List.of(),
                    List.of(value("revenue", AggregationType.SUM)),
                    List.of()
            );

            assertThatThrownBy(() -> builder.buildPivotQuery(cfg, "sales", VALID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid column");
        }

        @Test
        @DisplayName("SQL injection in column name is escaped")
        void sqlInjection() {
            var columns = Set.of("city; DROP TABLE --");
            var cfg = config(
                    List.of(new PivotFieldDto("city; DROP TABLE --", "test")),
                    List.of(),
                    List.of(value("city; DROP TABLE --", AggregationType.COUNT)),
                    List.of()
            );

            var result = builder.buildPivotQuery(cfg, "sales", columns);

            assertThat(result.sql()).contains("\"city; DROP TABLE --\"");
            assertThat(result.sql()).doesNotContain("DROP TABLE \"");
        }
    }

    @Test
    @DisplayName("qualifiedTable produces schema.table")
    void qualifiedTable() {
        assertThat(builder.qualifiedTable("public", "deals"))
                .isEqualTo("\"public\".\"deals\"");
    }
}
