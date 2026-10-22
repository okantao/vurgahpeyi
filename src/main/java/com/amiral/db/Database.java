package com.amiral.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        runScript(script);
        if (migrate()) {
            runScript(script); // recreate indexes of rebuilt tables
        }
    }

    private void runScript(String script) throws SQLException {
        try (Statement st = connection.createStatement()) {
            for (String sql : splitStatements(script)) {
                st.execute(sql);
            }
        }
    }

    /** Splits on ';' but keeps CREATE TRIGGER ... BEGIN ...; END; together. */
    static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String part : script.split(";")) {
            current.append(part);
            String text = current.toString().strip();
            String upper = text.toUpperCase(Locale.ROOT);
            if (upper.contains("CREATE TRIGGER") && !upper.endsWith("END")) {
                current.append(';');
                continue;
            }
            if (!text.isEmpty() && !isOnlyComments(text)) {
                statements.add(text);
            }
            current.setLength(0);
        }
        return statements;
    }

    private static boolean isOnlyComments(String text) {
        return text.lines().map(String::strip).allMatch(l -> l.isEmpty() || l.startsWith("--"));
    }

    /**
     * Upgrades a database created by v1.0: adds the AI game mode and the move.weapon column.
     *
     * @return true if something was changed
     */
    boolean migrate() throws SQLException {
        boolean changed = false;
        String gameSql = tableSql("game");
        if (gameSql != null && !gameSql.contains("'AI'")) {
            rebuildGameTable();
            changed = true;
        }
        if (!hasColumn("move", "weapon")) {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE move ADD COLUMN weapon TEXT NOT NULL DEFAULT 'SHOT' "
                        + "CHECK (weapon IN ('SHOT','TORPEDO'))");
            }
            changed = true;
        }
        return changed;
    }

    private void rebuildGameTable() throws SQLException {
        boolean auto = connection.getAutoCommit();
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");
            connection.setAutoCommit(false);
            st.execute("""
                    CREATE TABLE game_new (
                      id          INTEGER PRIMARY KEY AUTOINCREMENT,
                      mode        TEXT    NOT NULL CHECK (mode IN ('SERVER','CLIENT','AI')),
                      status      TEXT    NOT NULL CHECK (status IN ('ACTIVE','FINISHED','ABORTED')),
                      started_at  TEXT    NOT NULL DEFAULT (datetime('now')),
                      ended_at    TEXT,
                      winner_role TEXT    CHECK (winner_role IN ('LOCAL','REMOTE')),
                      remote_ip   TEXT,
                      port        INTEGER CHECK (port BETWEEN 1024 AND 65535)
                    )""");
            st.execute("INSERT INTO game_new (id, mode, status, started_at, ended_at, winner_role, remote_ip, port) "
                    + "SELECT id, mode, status, started_at, ended_at, winner_role, remote_ip, port FROM game");
            st.execute("DROP TABLE game");
            st.execute("ALTER TABLE game_new RENAME TO game");
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(auto);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
        }
    }

    private String tableSql(String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public Connection connection() {
        return connection;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
