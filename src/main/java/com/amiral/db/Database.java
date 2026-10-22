package com.amiral.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Holds the single JDBC connection to SQLite and installs the schema. */
public final class Database implements AutoCloseable {

    public static final String DEFAULT_URL = "jdbc:sqlite:data/amiral.db";
    public static final String MEMORY_URL = "jdbc:sqlite::memory:";
    private static final String FILE_PREFIX = "jdbc:sqlite:";

    private final Connection connection;

    private Database(Connection connection) {
        this.connection = connection;
    }

    /** Opens the database; for a file URL the parent directory is created if needed. */
    public static Database open(String jdbcUrl) throws SQLException {
        createParentDirectory(jdbcUrl);
        Connection c = DriverManager.getConnection(jdbcUrl);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 5000");
        }
        return new Database(c);
    }

    public static Database openDefault() throws SQLException {
        return open(DEFAULT_URL);
    }

    private static void createParentDirectory(String jdbcUrl) throws SQLException {
        if (!jdbcUrl.startsWith(FILE_PREFIX) || jdbcUrl.contains(":memory:")) {
            return;
        }
        Path parent = Path.of(jdbcUrl.substring(FILE_PREFIX.length())).toAbsolutePath().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new SQLException("Cannot create directory " + parent, e);
        }
    }

    /** Runs schema.sql from the classpath. Safe to call more than once. */
    public void initSchema() throws SQLException {
        String script;
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new SQLException("schema.sql not found on classpath");
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Cannot read schema.sql", e);
        }
        try (Statement st = connection.createStatement()) {
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) {
                    st.execute(sql.strip());
                }
            }
        }
    }

    public Connection connection() {
        return connection;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
