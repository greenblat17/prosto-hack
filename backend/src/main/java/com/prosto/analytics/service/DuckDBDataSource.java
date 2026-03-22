package com.prosto.analytics.service;

import org.duckdb.DuckDBConnection;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * Lightweight DataSource wrapper around a DuckDB master connection.
 * Each getConnection() call returns a duplicate (thread-safe for concurrent reads).
 */
public class DuckDBDataSource implements DataSource {

    private final DuckDBConnection masterConnection;

    public DuckDBDataSource(DuckDBConnection masterConnection) {
        this.masterConnection = masterConnection;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return masterConnection.duplicate();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        // DuckDB does not support log writers
    }

    @Override
    public void setLoginTimeout(int seconds) {
        // DuckDB connections are local, no network timeout needed
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException("Not a wrapper");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) { return false; }
}
