package com.amiral.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Access to the {@code app_log} table. */
public class LogRepository {

    public record LogEntry(long id, Long gameId, String level, String message, String createdAt) {
    }

    private final Database db;

    public LogRepository(Database db) {
        this.db = db;
    }

    public void info(Long gameId, String message) throws SQLException {
        write(gameId, "INFO", message);
    }

    public void warn(Long gameId, String message) throws SQLException {
        write(gameId, "WARN", message);
    }

    public void error(Long gameId, String message) throws SQLException {
        write(gameId, "ERROR", message);
    }

    private void write(Long gameId, String level, String message) throws SQLException {
        Jdbc.insert(db.connection(), "INSERT INTO app_log(game_id, level, message) VALUES (?, ?, ?)",
                gameId, level, message);
    }

    /** The latest entries, newest first. */
    public List<LogEntry> recent(int limit) throws SQLException {
        List<LogEntry> list = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id, game_id, level, message, created_at FROM app_log ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new LogEntry(rs.getLong(1), Jdbc.nullableLong(rs, "game_id"), rs.getString(3),
                            rs.getString(4), rs.getString(5)));
                }
            }
        }
        return list;
    }
}
