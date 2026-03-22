package com.prosto.analytics.service;

import com.prosto.analytics.dto.TableFieldDto;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class DuckDBCacheService {

    private static final Logger log = LoggerFactory.getLogger(DuckDBCacheService.class);
    private static final String DROP_IF_EXISTS = "DROP TABLE IF EXISTS ";
    private static final String DUCKDB_DOUBLE = "DOUBLE";

    private record CacheEntry(
            String host, int port, String database, String schema, String tableName,
            String duckTableName, long rowCount, long cachedAtMillis
    ) {}

    @Value("${app.duckdb.path:/data/duckdb-cache.duckdb}")
    private String duckdbPath;

    @Value("${app.duckdb.max-disk-gb:10}")
    private int maxDiskGb;

    @Value("${app.duckdb.ttl-minutes:30}")
    private int ttlMinutes;

    private DuckDBConnection masterConnection;
    private final ConcurrentHashMap<String, CacheEntry> cacheMetadata = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Void>> inFlightLoads = new ConcurrentHashMap<>();

    private final ReentrantLock writeLock = new ReentrantLock();

    private final ExecutorService cacheExecutor = Executors.newFixedThreadPool(2,
            r -> { var t = new Thread(r, "duckdb-cache"); t.setDaemon(true); return t; });

    @PostConstruct
    public void init() {
        try {
            masterConnection = openConnection();
            log.info("DuckDB cache initialized: {}", duckdbPath);
        } catch (Exception e) {
            log.warn("DuckDB cache file corrupt, recreating: {}", e.getMessage());
            try {
                Files.deleteIfExists(Path.of(duckdbPath));
            } catch (IOException deleteEx) {
                log.warn("Failed to delete corrupt DuckDB file: {}", deleteEx.getMessage());
            }
            try {
                masterConnection = openConnection();
                log.info("DuckDB cache recreated: {}", duckdbPath);
            } catch (Exception ex) {
                log.error("Failed to initialize DuckDB cache: {}", ex.getMessage(), ex);
                throw new IllegalStateException("DuckDB initialization failed", ex);
            }
        }
    }

    private DuckDBConnection openConnection() throws SQLException {
        File parent = new File(duckdbPath).getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        return (DuckDBConnection) DriverManager.getConnection("jdbc:duckdb:" + duckdbPath);
    }

    @PreDestroy
    public void cleanup() {
        cacheExecutor.shutdownNow();
        if (masterConnection != null) {
            try {
                masterConnection.close();
                log.info("DuckDB cache closed");
            } catch (SQLException e) {
                log.warn("Error closing DuckDB: {}", e.getMessage());
            }
        }
    }

    /**
     * Stable cache table name based on host:port:db — survives reconnection.
     */
    public String cacheTableName(String host, int port, String database, String schema, String tableName) {
        String raw = "cache_" + host + "_" + port + "_" + database + "_" + schema + "_" + tableName;
        return raw.replaceAll("\\W", "_");
    }

    public boolean isTableCached(String host, int port, String database, String schema, String tableName) {
        String key = cacheKey(host, port, database, schema, tableName);
        CacheEntry entry = cacheMetadata.get(key);
        if (entry == null) return false;

        long ttlMs = ttlMinutes * 60_000L;
        if (System.currentTimeMillis() - entry.cachedAtMillis() > ttlMs) {
            evictTable(host, port, database, schema, tableName);
            return false;
        }
        return true;
    }

    public boolean isTableLoading(String host, int port, String database, String schema, String tableName) {
        return inFlightLoads.containsKey(cacheKey(host, port, database, schema, tableName));
    }

    public JdbcTemplate getDuckDBJdbc() {
        return new JdbcTemplate(new DuckDBDataSource(masterConnection));
    }

    public CompletableFuture<Void> cacheTableAsync(String connectionId, String schema,
                                                    String tableName,
                                                    ConnectionService connectionService,
                                                    String userEmail) {
        ConnectionService.ConnectionInfo info = connectionService.getConnectionInfo(connectionId, userEmail);
        String key = cacheKey(info.host(), info.port(), info.database(), schema, tableName);

        if (isTableCached(info.host(), info.port(), info.database(), schema, tableName)) {
            return CompletableFuture.completedFuture(null);
        }

        return inFlightLoads.computeIfAbsent(key, k ->
                CompletableFuture.runAsync(
                        () -> doCacheTable(info, schema, tableName, connectionService, connectionId, userEmail),
                        cacheExecutor
                ).whenComplete((v, ex) -> {
                    inFlightLoads.remove(key);
                    if (ex != null) {
                        log.error("Cache failed for {}.{}: {}", schema, tableName, ex.getMessage(), ex);
                    }
                })
        );
    }

    private void doCacheTable(ConnectionService.ConnectionInfo info, String schema, String tableName,
                              ConnectionService connectionService, String connectionId, String userEmail) {
        long startTime = System.currentTimeMillis();
        String duckTable = cacheTableName(info.host(), info.port(), info.database(), schema, tableName);

        try {
            // 1. Get column metadata from external DB
            List<TableFieldDto> fields = connectionService.getTableFields(connectionId, schema, tableName, userEmail);
            if (fields.isEmpty()) {
                log.warn("No fields found for {}.{}, skipping cache", schema, tableName);
                return;
            }

            // 2. Create table in DuckDB
            String createSql = buildCreateTableSql(duckTable, fields);
            writeLock.lock();
            try (var duckConn = masterConnection.duplicate();
                 Statement stmt = duckConn.createStatement()) {
                stmt.execute(DROP_IF_EXISTS + ident(duckTable));
                stmt.execute(createSql);
            } finally {
                writeLock.unlock();
            }

            // 3. Read from PostgreSQL via COPY TO STDOUT, write to DuckDB via Appender
            JdbcTemplate extJdbc = connectionService.getJdbc(connectionId, userEmail);
            String copySql = "COPY " + ident(schema) + "." + ident(tableName)
                    + " TO STDOUT WITH (FORMAT CSV, HEADER false, NULL '')";

            long rowCount = 0;

            try (Connection pgConn = extJdbc.getDataSource().getConnection()) {
                BaseConnection basePgConn = pgConn.unwrap(BaseConnection.class);
                CopyManager copyManager = new CopyManager(basePgConn);

                var pis = new PipedInputStream(1024 * 1024);
                var pos = new PipedOutputStream(pis);

                Thread copyThread = Thread.startVirtualThread(() -> {
                    try {
                        copyManager.copyOut(copySql, pos);
                        pos.close();
                    } catch (Exception e) {
                        closeSilently(pos);
                        log.error("COPY OUT failed: {}", e.getMessage());
                    }
                });

                try (var reader = new BufferedReader(new InputStreamReader(pis, StandardCharsets.UTF_8));
                     var csvParser = CSVFormat.DEFAULT.builder().build().parse(reader);
                     var duckConn = (DuckDBConnection) masterConnection.duplicate();
                     var appender = duckConn.createAppender(DuckDBConnection.DEFAULT_SCHEMA, duckTable)) {

                    for (CSVRecord csvRow : csvParser) {
                        appender.beginRow();
                        for (int i = 0; i < fields.size(); i++) {
                            String val = csvRow.get(i);
                            appendTypedValue(appender, val, fields.get(i).type());
                        }
                        appender.endRow();
                        rowCount++;

                        if (rowCount % 500_000 == 0) {
                            log.info("Caching {}.{}: {} rows...", schema, tableName, rowCount);
                        }
                    }
                    appender.flush();
                }

                copyThread.join();
            }

            // 4. Store metadata
            String key = cacheKey(info.host(), info.port(), info.database(), schema, tableName);
            cacheMetadata.put(key, new CacheEntry(
                    info.host(), info.port(), info.database(), schema, tableName, duckTable,
                    rowCount, System.currentTimeMillis()));

            long duration = System.currentTimeMillis() - startTime;
            log.info("Cached {}.{}: {} rows in {}ms (table: {})",
                    schema, tableName, rowCount, duration, duckTable);

            evictIfOverLimit();

        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            writeLock.lock();
            try (var conn = masterConnection.duplicate();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(DROP_IF_EXISTS + ident(duckTable));
            } catch (SQLException dropEx) {
                log.warn("Failed to drop partial cache table {}: {}", duckTable, dropEx.getMessage());
            } finally {
                writeLock.unlock();
            }
            throw new IllegalStateException("Failed to cache " + schema + "." + tableName, e);
        }
    }

    private String buildCreateTableSql(String duckTable, List<TableFieldDto> fields) {
        var sb = new StringBuilder("CREATE TABLE " + ident(duckTable) + " (");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(ident(fields.get(i).name())).append(" ").append(mapPgType(fields.get(i).type()));
        }
        sb.append(")");
        return sb.toString();
    }

    private void appendTypedValue(org.duckdb.DuckDBAppender appender, String val, String pgType)
            throws SQLException {
        if (val == null || val.isEmpty()) {
            appender.append((String) null);
            return;
        }
        try {
            switch (mapPgType(pgType)) {
                case "INTEGER", "SMALLINT" -> appender.append(Integer.parseInt(val));
                case "BIGINT" -> appender.append(Long.parseLong(val));
                case DUCKDB_DOUBLE, "FLOAT" -> appender.append(Double.parseDouble(val));
                case "BOOLEAN" -> appender.append(Boolean.parseBoolean(val));
                default -> appender.append(val);
            }
        } catch (NumberFormatException nfe) {
            log.trace("Cannot parse '{}' as {}: {}", val, pgType, nfe.getMessage());
            appender.append(val);
        }
    }

    private String mapPgType(String pgType) {
        if (pgType == null) return "VARCHAR";
        return switch (pgType.toLowerCase()) {
            case "integer", "int4", "int", "serial" -> "INTEGER";
            case "bigint", "int8", "bigserial" -> "BIGINT";
            case "smallint", "int2" -> "SMALLINT";
            case "numeric", "decimal" -> DUCKDB_DOUBLE;
            case "real", "float4" -> "FLOAT";
            case "double precision", "float8" -> DUCKDB_DOUBLE;
            case "boolean", "bool" -> "BOOLEAN";
            case "date" -> "DATE";
            default -> {
                if (pgType.toLowerCase().startsWith("timestamp")) yield "TIMESTAMP";
                yield "VARCHAR";
            }
        };
    }

    public void evictTable(String host, int port, String database, String schema, String tableName) {
        String key = cacheKey(host, port, database, schema, tableName);
        CacheEntry entry = cacheMetadata.remove(key);
        if (entry != null) {
            writeLock.lock();
            try (var conn = masterConnection.duplicate();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(DROP_IF_EXISTS + ident(entry.duckTableName()));
                log.info("Evicted cache: {}.{} (table: {})", schema, tableName, entry.duckTableName());
            } catch (Exception e) {
                log.warn("Failed to drop cache table {}: {}", entry.duckTableName(), e.getMessage());
            } finally {
                writeLock.unlock();
            }
        }
    }

    public void evictByConnection(String connectionId, ConnectionService connectionService, String userEmail) {
        try {
            ConnectionService.ConnectionInfo info = connectionService.getConnectionInfo(connectionId, userEmail);
            List<CacheEntry> toEvict = cacheMetadata.values().stream()
                    .filter(e -> e.host().equals(info.host()) && e.port() == info.port()
                            && e.database().equals(info.database()))
                    .toList();

            for (CacheEntry entry : toEvict) {
                evictTable(entry.host(), entry.port(), entry.database(), entry.schema(), entry.tableName());
            }

            if (!toEvict.isEmpty()) {
                log.info("Evicted all caches for {}:{}/{} ({} tables)",
                        info.host(), info.port(), info.database(), toEvict.size());
            }
        } catch (Exception e) {
            log.warn("Could not evict caches for connection {}: {}", connectionId, e.getMessage());
        }
    }

    @Scheduled(fixedRate = 60_000)
    public void evictExpired() {
        long ttlMs = ttlMinutes * 60_000L;
        long now = System.currentTimeMillis();

        List<CacheEntry> expired = cacheMetadata.values().stream()
                .filter(e -> now - e.cachedAtMillis() > ttlMs)
                .toList();

        for (CacheEntry entry : expired) {
            evictTable(entry.host(), entry.port(), entry.database(), entry.schema(), entry.tableName());
        }
    }

    private void evictIfOverLimit() {
        long maxBytes = maxDiskGb * 1024L * 1024L * 1024L;
        long currentSize = getDatabaseSizeBytes();

        while (currentSize > maxBytes && !cacheMetadata.isEmpty()) {
            CacheEntry oldest = cacheMetadata.values().stream()
                    .min(Comparator.comparingLong(CacheEntry::cachedAtMillis))
                    .orElse(null);

            if (oldest == null) break;

            log.info("Cache over limit ({} MB > {} MB), evicting oldest: {}.{}",
                    currentSize / 1024 / 1024, maxBytes / 1024 / 1024,
                    oldest.schema(), oldest.tableName());

            evictTable(oldest.host(), oldest.port(), oldest.database(), oldest.schema(), oldest.tableName());
            currentSize = getDatabaseSizeBytes();
        }
    }

    private long getDatabaseSizeBytes() {
        writeLock.lock();
        try (var conn = masterConnection.duplicate();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT total_blocks * block_size AS size_bytes FROM pragma_database_size()")) {
            if (rs.next()) {
                return rs.getLong("size_bytes");
            }
        } catch (Exception e) {
            log.warn("Failed to get DuckDB database size: {}", e.getMessage());
        } finally {
            writeLock.unlock();
        }
        return 0;
    }

    private String cacheKey(String host, int port, String database, String schema, String tableName) {
        return host + ":" + port + "/" + database + "::" + schema + "::" + tableName;
    }

    private String ident(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private static void closeSilently(Closeable resource) {
        try {
            resource.close();
        } catch (IOException e) {
            log.trace("Failed to close resource: {}", e.getMessage());
        }
    }
}
