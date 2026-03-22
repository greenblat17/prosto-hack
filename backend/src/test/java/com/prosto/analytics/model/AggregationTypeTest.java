package com.prosto.analytics.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

class AggregationTypeTest {

    @Nested
    @DisplayName("fromValue")
    class FromValue {

        @Test
        void parsesAllValues() {
            for (AggregationType at : AggregationType.values()) {
                assertThat(AggregationType.fromValue(at.getValue())).isEqualTo(at);
            }
        }

        @Test
        void caseInsensitive() {
            assertThat(AggregationType.fromValue("SUM")).isEqualTo(AggregationType.SUM);
            assertThat(AggregationType.fromValue("Original")).isEqualTo(AggregationType.ORIGINAL);
        }

        @Test
        void unknownThrows() {
            assertThatThrownBy(() -> AggregationType.fromValue("unknown"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("toSqlExpression — PostgreSQL")
    class PostgreSql {

        @Test
        void original() {
            assertThat(AggregationType.ORIGINAL.toSqlExpression("\"col\""))
                    .isEqualTo("\"col\"");
        }

        @Test
        void sum() {
            assertThat(AggregationType.SUM.toSqlExpression("\"revenue\""))
                    .isEqualTo("SUM(\"revenue\")");
        }

        @Test
        void countDistinct() {
            assertThat(AggregationType.COUNT_DISTINCT.toSqlExpression("\"city\""))
                    .isEqualTo("COUNT(DISTINCT \"city\")");
        }

        @Test
        void median() {
            assertThat(AggregationType.MEDIAN.toSqlExpression("\"price\""))
                    .contains("PERCENTILE_CONT");
        }

        @Test
        void intSum() {
            assertThat(AggregationType.INT_SUM.toSqlExpression("\"qty\""))
                    .isEqualTo("SUM(\"qty\")::BIGINT");
        }

        @Test
        void listDistinct() {
            assertThat(AggregationType.LIST_DISTINCT.toSqlExpression("\"city\""))
                    .contains("STRING_AGG(DISTINCT")
                    .contains("::text");
        }

        @Test
        void first() {
            assertThat(AggregationType.FIRST.toSqlExpression("\"col\""))
                    .isEqualTo("(ARRAY_AGG(\"col\"))[1]");
        }

        @Test
        void last() {
            assertThat(AggregationType.LAST.toSqlExpression("\"col\""))
                    .contains("ARRAY_AGG(\"col\" ORDER BY \"col\" DESC)");
        }
    }

    @Nested
    @DisplayName("toSqlExpression — DuckDB")
    class DuckDb {

        @Test
        void median() {
            assertThat(AggregationType.MEDIAN.toSqlExpression("\"price\"", true))
                    .isEqualTo("MEDIAN(\"price\")");
        }

        @Test
        void intSum() {
            assertThat(AggregationType.INT_SUM.toSqlExpression("\"qty\"", true))
                    .isEqualTo("CAST(SUM(\"qty\") AS BIGINT)");
        }

        @Test
        void listDistinct() {
            assertThat(AggregationType.LIST_DISTINCT.toSqlExpression("\"city\"", true))
                    .contains("STRING_AGG(DISTINCT CAST(")
                    .contains("AS VARCHAR)");
        }

        @Test
        void first() {
            assertThat(AggregationType.FIRST.toSqlExpression("\"col\"", true))
                    .isEqualTo("FIRST(\"col\")");
        }

        @Test
        void last() {
            assertThat(AggregationType.LAST.toSqlExpression("\"col\"", true))
                    .isEqualTo("LAST(\"col\")");
        }
    }

    @Nested
    @DisplayName("Metadata methods")
    class Metadata {

        @ParameterizedTest
        @EnumSource(AggregationType.class)
        void everyTypeHasDisplayLabel(AggregationType agg) {
            assertThat(agg.getDisplayLabel()).isNotBlank();
        }

        @Test
        void windowFunctions() {
            assertThat(AggregationType.RUNNING_SUM.isWindowFunction()).isTrue();
            assertThat(AggregationType.SUM_PCT_TOTAL.isWindowFunction()).isTrue();
            assertThat(AggregationType.COUNT_PCT_ROW.isWindowFunction()).isTrue();
            assertThat(AggregationType.SUM.isWindowFunction()).isFalse();
            assertThat(AggregationType.ORIGINAL.isWindowFunction()).isFalse();
        }

        @Test
        void numericRequirement() {
            assertThat(AggregationType.SUM.requiresNumericColumn()).isTrue();
            assertThat(AggregationType.AVG.requiresNumericColumn()).isTrue();
            assertThat(AggregationType.COUNT.requiresNumericColumn()).isFalse();
            assertThat(AggregationType.ORIGINAL.requiresNumericColumn()).isFalse();
            assertThat(AggregationType.LIST_DISTINCT.requiresNumericColumn()).isFalse();
        }

        @Test
        void returnsText() {
            assertThat(AggregationType.LIST_DISTINCT.returnsText()).isTrue();
            assertThat(AggregationType.SUM.returnsText()).isFalse();
        }
    }
}
