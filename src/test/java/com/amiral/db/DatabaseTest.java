package com.amiral.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTest {

    private static Set<String> tables(Database db) throws SQLException {
        Set<String> names = new HashSet<>();
        try (Statement st = db.connection().createStatement();
                ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    @Test
    void createsAllTables() throws SQLException {
        try (Database db = Database.open(Database.MEMORY_URL)) {
            db.initSchema();
            assertTrue(tables(db).containsAll(Set.of("player", "game", "game_player", "ship_placement", "move",
                    "chat_message", "app_log")));
        }
    }

    @Test
    void initSchemaIsIdempotent() throws SQLException {
        try (Database db = Database.open(Database.MEMORY_URL)) {
            db.initSchema();
            assertDoesNotThrow(db::initSchema);
        }
    }

    @Test
    void foreignKeysAreOn() throws SQLException {
        try (Database db = Database.open(Database.MEMORY_URL);
                Statement st = db.connection().createStatement();
                ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void createsFileAndParentDirectory(@TempDir Path dir) throws SQLException {
        Path file = dir.resolve("nested").resolve("amiral.db");
        try (Database db = Database.open("jdbc:sqlite:" + file)) {
            db.initSchema();
        }
        assertTrue(Files.exists(file));
    }
}
