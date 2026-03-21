package com.prosto.analytics.service;

import com.prosto.analytics.dto.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionService.class);
    private static final int MAX_CONNECTIONS_PER_USER = 3;
    private static final long IDLE_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

    public record ConnectionInfo(String host, int port, String database, String username, String password) {}

    private record PooledConnection(HikariDataSource dataSource, JdbcTemplate jdbcTemplate,
                                     String ownerEmail, String name, String host, int port,
                                     String database, String username, String password, Instant lastUsed) {}

    private final ConcurrentHashMap<String, PooledConnection> connections = new ConcurrentHashMap<>();

    public ConnectionTestResultDto testConnection(ConnectionRequestDto request) {
        String url = buildJdbcUrl(request.host(), request.port(), request.database());
        try (Connection conn = DriverManager.getConnection(url, request.username(), request.password())) {
            if (conn.isValid(5)) {
                return new ConnectionTestResultDto(true, "Соединение установлено успешно");
            }
            return new ConnectionTestResultDto(false, "Соединение не прошло валидацию");
        } catch (Exception e) {
            return new ConnectionTestResultDto(false, "Ошибка подключения: " + e.getMessage());
        }
    }

    public ConnectionResponseDto connect(ConnectionRequestDto request, String userEmail) {
        long userCount = connections.values().stream()
                .filter(c -> c.ownerEmail().equals(userEmail))
                .count();
        if (userCount >= MAX_CONNECTIONS_PER_USER) {
            throw new IllegalStateException(
                    "Превышен лимит подключений (" + MAX_CONNECTIONS_PER_USER + "). Отключите неиспользуемые.");
        }

        String connectionId = UUID.randomUUID().toString();
        String displayName = (request.name() != null && !request.name().isBlank())
                ? request.name()
                : request.host() + ":" + request.port() + "/" + request.database();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(buildJdbcUrl(request.host(), request.port(), request.database()));
        config.setUsername(request.username());
        config.setPassword(request.password());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(600_000);
        config.setPoolName("ext-" + connectionId.substring(0, 8));

        HikariDataSource ds = new HikariDataSource(config);
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
        } catch (Exception e) {
            ds.close();
            throw new IllegalArgumentException("Не удалось подключиться: " + e.getMessage());
        }

        connections.put(connectionId, new PooledConnection(ds, jdbc, userEmail, displayName,
                request.host(), request.port(), request.database(),
                request.username(), request.password(), Instant.now()));

        log.info("External connection established: {} by {} -> {}:{}/{}", connectionId, userEmail,
                request.host(), request.port(), request.database());

        return new ConnectionResponseDto(connectionId, displayName, request.host(), request.port(), request.database());
    }

    public void disconnect(String connectionId, String userEmail) {
        PooledConnection conn = connections.get(connectionId);
        if (conn == null) return;
        validateOwner(conn, userEmail);
        connections.remove(connectionId);
        conn.dataSource().close();
        log.info("External connection closed: {} by {}", connectionId, userEmail);
    }

    public List<SchemaInfoDto> getSchemas(String connectionId, String userEmail) {
        JdbcTemplate jdbc = getJdbc(connectionId, userEmail);
        return jdbc.queryForList(
                "SELECT schema_name FROM information_schema.schemata " +
                "WHERE schema_name NOT IN ('information_schema', 'pg_catalog', 'pg_toast') " +
                "ORDER BY schema_name"
        ).stream()
                .map(row -> new SchemaInfoDto(row.get("schema_name").toString()))
                .toList();
    }

    public List<TableInfoDto> getTables(String connectionId, String schema, String userEmail) {
        JdbcTemplate jdbc = getJdbc(connectionId, userEmail);
        return jdbc.queryForList(
                "SELECT t.table_name, t.table_schema, " +
                "  (SELECT reltuples::bigint FROM pg_class c JOIN pg_namespace n ON c.relnamespace = n.oid " +
                "   WHERE c.relname = t.table_name AND n.nspname = t.table_schema) AS estimated_rows " +
                "FROM information_schema.tables t " +
                "WHERE t.table_schema = ? AND t.table_type IN ('BASE TABLE', 'VIEW') " +
                "ORDER BY t.table_name",
                schema
        ).stream()
                .map(row -> new TableInfoDto(
                        row.get("table_name").toString(),
                        row.get("table_schema").toString(),
                        row.get("estimated_rows") != null ? ((Number) row.get("estimated_rows")).longValue() : null
                ))
                .toList();
    }

    public List<TableFieldDto> getTableFields(String connectionId, String schema, String table, String userEmail) {
        JdbcTemplate jdbc = getJdbc(connectionId, userEmail);
        return jdbc.queryForList(
                "SELECT column_name, data_type, is_nullable " +
                "FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = ? " +
                "ORDER BY ordinal_position",
                schema, table
        ).stream()
                .map(row -> new TableFieldDto(
                        row.get("column_name").toString(),
                        row.get("data_type").toString(),
                        "YES".equals(row.get("is_nullable").toString())
                ))
                .toList();
    }

    public JdbcTemplate getJdbc(String connectionId, String userEmail) {
        PooledConnection conn = connections.get(connectionId);
        if (conn == null) {
            throw new NoSuchElementException("Connection not found: " + connectionId);
        }
        validateOwner(conn, userEmail);
        touchConnection(connectionId, conn);
        return conn.jdbcTemplate();
    }

    public Set<String> getColumnNames(String connectionId, String schema, String table, String userEmail) {
        JdbcTemplate jdbc = getJdbc(connectionId, userEmail);
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position",
                schema, table
        ).stream().map(row -> row.get("column_name").toString()).toList());
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanupIdleConnections() {
        Instant cutoff = Instant.now().minusMillis(IDLE_TIMEOUT_MS);
        List<String> expired = connections.entrySet().stream()
                .filter(e -> e.getValue().lastUsed().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .toList();

        for (String id : expired) {
            PooledConnection conn = connections.remove(id);
            if (conn != null) {
                conn.dataSource().close();
                log.info("Auto-closed idle connection: {} (owner: {}, idle > {} min)",
                        id, conn.ownerEmail(), IDLE_TIMEOUT_MS / 60_000);
            }
        }
    }

    private void validateOwner(PooledConnection conn, String userEmail) {
        if (!conn.ownerEmail().equals(userEmail)) {
            throw new SecurityException("Нет доступа к этому подключению");
        }
    }

    public long getTableRowCount(String connectionId, String schema, String table, String userEmail) {
        JdbcTemplate jdbc = getJdbc(connectionId, userEmail);
        String sql = "SELECT reltuples::bigint FROM pg_class c JOIN pg_namespace n ON c.relnamespace = n.oid " +
                "WHERE n.nspname = ? AND c.relname = ?";
        Long count = jdbc.queryForObject(sql, Long.class, schema, table);
        return count != null && count >= 0 ? count : 0;
    }

    public ConnectionInfo getConnectionInfo(String connectionId, String userEmail) {
        PooledConnection conn = connections.get(connectionId);
        if (conn == null) {
            throw new NoSuchElementException("Connection not found: " + connectionId);
        }
        validateOwner(conn, userEmail);
        return new ConnectionInfo(conn.host(), conn.port(), conn.database(), conn.username(), conn.password());
    }

    private void touchConnection(String connectionId, PooledConnection conn) {
        connections.put(connectionId, new PooledConnection(
                conn.dataSource(), conn.jdbcTemplate(), conn.ownerEmail(), conn.name(),
                conn.host(), conn.port(), conn.database(),
                conn.username(), conn.password(), Instant.now()));
    }

    private String buildJdbcUrl(String host, int port, String database) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    @PreDestroy
    public void closeAll() {
        connections.forEach((id, conn) -> {
            try {
                conn.dataSource().close();
                log.info("Closed external connection on shutdown: {}", id);
            } catch (Exception e) {
                log.warn("Error closing connection {}: {}", id, e.getMessage());
            }
        });
        connections.clear();
    }
}
