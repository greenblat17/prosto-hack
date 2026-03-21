package com.prosto.analytics.service;

import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class DuckDBCacheService {

    private static final Logger log = LoggerFactory.getLogger(DuckDBCacheService.class);

    private record CacheEntry(
            String connectionId, String schema, String tableName,
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

    // DuckDB allows only one concurrent writer per database file.
    // All DDL operations (CREATE TABLE, DROP TABLE, ATTACH, DETACH) must be serialized.
    private final ReentrantLock writeLock = new ReentrantLock();

    // Dedicated executor for background cache loading (bounded to avoid ForkJoinPool starvation)
    private final ExecutorService cacheExecutor = Executors.newFixedThreadPool(2,
            r -> { var t = new Thread(r, "duckdb-cache"); t.setDaemon(true); return t; });

    @PostConstruct
    public void init() {
        try {
            masterConnection = openConnection();
            installPostgresExtension();
            log.info("DuckDB cache initialized: {}", duckdbPath);
        } catch (Exception e) {
            log.warn("DuckDB cache file corrupt or unreadable, recreating: {}", e.getMessage());
            new File(duckdbPath).delete();
            try {
                masterConnection = openConnection();
                installPostgresExtension();
                log.info("DuckDB cache recreated: {}", duckdbPath);
            } catch (Exception ex) {
                log.error("Failed to initialize DuckDB cache: {}", ex.getMessage(), ex);
                throw new RuntimeException("DuckDB initialization failed", ex);
            }
        }
    }

    private void installPostgresExtension() throws SQLException {
        try (Statement stmt = masterConnection.createStatement()) {
            stmt.execute("INSTALL postgres");
            stmt.execute("LOAD postgres");
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

    public String cacheTableName(String connectionId, String schema, String tableName) {
        String prefix = connectionId.length() >= 8 ? connectionId.substring(0, 8) : connectionId;
        String raw = "cache_" + prefix + "_" + schema + "_" + tableName;
        return raw.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    public boolean isTableCached(String connectionId, String schema, String tableName) {
        String key = cacheKey(connectionId, schema, tableName);
        CacheEntry entry = cacheMetadata.get(key);
        if (entry == null) return false;

        long ttlMs = ttlMinutes * 60_000L;
        if (System.currentTimeMillis() - entry.cachedAtMillis() > ttlMs) {
            evictTable(connectionId, schema, tableName);
            return false;
        }
        return true;
    }

    public boolean isTableLoading(String connectionId, String schema, String tableName) {
        return inFlightLoads.containsKey(cacheKey(connectionId, schema, tableName));
    }

    /**
     * Get a JdbcTemplate for DuckDB reads. Each getConnection() call returns a
     * duplicate connection (thread-safe for concurrent reads).
     */
    public JdbcTemplate getDuckDBJdbc() {
        return new JdbcTemplate(new DuckDBDataSource(masterConnection));
    }

    /**
     * Start async background caching. If already cached or loading, returns immediately.
     */
    public CompletableFuture<Void> cacheTableAsync(String connectionId, String schema,
                                                    String tableName,
                                                    ConnectionService connectionService,
                                                    String userEmail) {
        String key = cacheKey(connectionId, schema, tableName);

        if (isTableCached(connectionId, schema, tableName)) {
            return CompletableFuture.completedFuture(null);
        }

        return inFlightLoads.computeIfAbsent(key, k ->
                CompletableFuture.runAsync(
                        () -> doCacheTable(connectionId, schema, tableName, connectionService, userEmail),
                        cacheExecutor
                ).whenComplete((v, ex) -> {
                    inFlightLoads.remove(key);
                    if (ex != null) {
                        log.error("Cache failed for {}.{}: {}", schema, tableName, ex.getMessage());
                    }
                })
        );
    }

    private void doCacheTable(String connectionId, String schema, String tableName,
                              ConnectionService connectionService, String userEmail) {
        long startTime = System.currentTimeMillis();
        String duckTable = cacheTableName(connectionId, schema, tableName);

        try {
            ConnectionService.ConnectionInfo info = connectionService.getConnectionInfo(connectionId, userEmail);
            String attachUrl = String.format("postgresql://%s:%s@%s:%d/%s",
                    info.username(), info.password(), info.host(), info.port(), info.database());

            String attachName = "src_" + duckTable;

            // Determine sort columns before acquiring write lock (read-only operation on external DB)
            String sortClause = determineSortClause(connectionService, connectionId, schema, tableName, userEmail);

            // All DDL must be serialized — DuckDB allows only one concurrent writer
            writeLock.lock();
            try (var conn = masterConnection.duplicate();
                 Statement stmt = conn.createStatement()) {

                stmt.execute("ATTACH '" + attachUrl + "' AS " + ident(attachName) + " (TYPE postgres, READ_ONLY)");

                try {
                    String createSql = "CREATE OR REPLACE TABLE " + ident(duckTable) + " AS SELECT * FROM "
                            + ident(attachName) + "." + ident(schema) + "." + ident(tableName);
                    if (!sortClause.isEmpty()) {
                        createSql += " ORDER BY " + sortClause;
                    }
                    stmt.execute(createSql);

                    long rowCount;
                    try (var rs = stmt.executeQuery("SELECT COUNT(*) FROM " + ident(duckTable))) {
                        rowCount = rs.next() ? rs.getLong(1) : 0;
                    }

                    String key = cacheKey(connectionId, schema, tableName);
                    cacheMetadata.put(key, new CacheEntry(
                            connectionId, schema, tableName, duckTable,
                            rowCount, System.currentTimeMillis()));

                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Cached {}.{}: {} rows in {}ms (table: {})",
                            schema, tableName, rowCount, duration, duckTable);

                    evictIfOverLimit();

                } finally {
                    try {
                        stmt.execute("DETACH " + ident(attachName));
                    } catch (Exception e) {
                        log.warn("Failed to detach {}: {}", attachName, e.getMessage());
                    }
                }
            } finally {
                writeLock.unlock();
            }
        } catch (Exception e) {
            // Cleanup partial table on failure
            writeLock.lock();
            try (var conn = masterConnection.duplicate();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + ident(duckTable));
            } catch (Exception dropEx) {
                log.warn("Failed to drop partial cache table {}: {}", duckTable, dropEx.getMessage());
            } finally {
                writeLock.unlock();
            }
            throw new RuntimeException("Failed to cache " + schema + "." + tableName, e);
        }
    }

    private String determineSortClause(ConnectionService connectionService, String connectionId,
                                        String schema, String tableName, String userEmail) {
        try {
            var fields = connectionService.getTableFields(connectionId, schema, tableName, userEmail);
            List<String> textColumns = fields.stream()
                    .filter(f -> f.type().contains("character") || f.type().equals("text")
                            || f.type().contains("varchar"))
                    .map(f -> ident(f.name()))
                    .limit(3)
                    .toList();
            return String.join(", ", textColumns);
        } catch (Exception e) {
            log.warn("Could not determine sort columns for {}.{}: {}", schema, tableName, e.getMessage());
            return "";
        }
    }

    public void evictTable(String connectionId, String schema, String tableName) {
        String key = cacheKey(connectionId, schema, tableName);
        CacheEntry entry = cacheMetadata.remove(key);
        if (entry != null) {
            writeLock.lock();
            try (var conn = masterConnection.duplicate();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + ident(entry.duckTableName()));
                log.info("Evicted cache: {}.{} (table: {})", schema, tableName, entry.duckTableName());
            } catch (Exception e) {
                log.warn("Failed to drop cache table {}: {}", entry.duckTableName(), e.getMessage());
            } finally {
                writeLock.unlock();
            }
        }
    }

    public void evictByConnection(String connectionId) {
        List<CacheEntry> toEvict = cacheMetadata.values().stream()
                .filter(e -> e.connectionId().equals(connectionId))
                .toList();

        for (CacheEntry entry : toEvict) {
            evictTable(entry.connectionId(), entry.schema(), entry.tableName());
        }

        inFlightLoads.entrySet().removeIf(e -> e.getKey().startsWith(connectionId));

        if (!toEvict.isEmpty()) {
            log.info("Evicted all caches for connection {} ({} tables)", connectionId, toEvict.size());
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
            evictTable(entry.connectionId(), entry.schema(), entry.tableName());
        }
    }

    private void evictIfOverLimit() {
        // Called while writeLock is held — getDatabaseSizeBytes acquires its own lock,
        // but ReentrantLock is reentrant so this is safe.
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

            // evictTable acquires writeLock — reentrant, so safe
            evictTable(oldest.connectionId(), oldest.schema(), oldest.tableName());
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

    private String cacheKey(String connectionId, String schema, String tableName) {
        return connectionId + "::" + schema + "::" + tableName;
    }

    private String ident(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
