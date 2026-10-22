package com.amiral.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Access to the {@code player} table. */
public class PlayerRepository {

    public record Player(long id, String nickname, String createdAt) {
    }

    private final Database db;

    public PlayerRepository(Database db) {
        this.db = db;
    }

    /** Returns the id of the player with this nickname, inserting it first if needed. */
    public long upsert(String nickname) throws SQLException {
        Jdbc.update(db.connection(), "INSERT OR IGNORE INTO player(nickname) VALUES (?)", nickname);
        return findByNickname(nickname).orElseThrow(() -> new SQLException("Player not stored: " + nickname)).id();
    }

    public Optional<Player> findByNickname(String nickname) throws SQLException {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id, nickname, created_at FROM player WHERE nickname = ?")) {
            ps.setString(1, nickname);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Player(rs.getLong("id"), rs.getString("nickname"), rs.getString("created_at")));
            }
        }
    }
}
