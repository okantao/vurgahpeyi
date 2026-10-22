package com.amiral.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Access to the {@code game} and {@code game_player} tables. */
public class GameRepository {

    public static final String LOCAL = "LOCAL";
    public static final String REMOTE = "REMOTE";

    public record GameRecord(long id, String mode, String status, String startedAt, String endedAt,
            String winnerRole, String remoteIp, Integer port) {
    }

    private final Database db;

    public GameRepository(Database db) {
        this.db = db;
    }

    /** Creates an ACTIVE game. {@code mode} is SERVER or CLIENT. */
    public long createGame(String mode, String remoteIp, Integer port) throws SQLException {
        return Jdbc.insert(db.connection(),
                "INSERT INTO game(mode, status, remote_ip, port) VALUES (?, 'ACTIVE', ?, ?)",
                mode, remoteIp, port);
    }

    public void updateRemoteIp(long gameId, String remoteIp) throws SQLException {
        Jdbc.update(db.connection(), "UPDATE game SET remote_ip = ? WHERE id = ?", remoteIp, gameId);
    }

    public void finish(long gameId, String winnerRole) throws SQLException {
        Jdbc.update(db.connection(),
                "UPDATE game SET status = 'FINISHED', winner_role = ?, ended_at = datetime('now') WHERE id = ?",
                winnerRole, gameId);
    }

    /** Marks an ACTIVE game as ABORTED; finished games are left alone. */
    public void abort(long gameId) throws SQLException {
        Jdbc.update(db.connection(),
                "UPDATE game SET status = 'ABORTED', ended_at = datetime('now') WHERE id = ? AND status = 'ACTIVE'",
                gameId);
    }

    public void linkPlayers(long gameId, long localId, long remoteId) throws SQLException {
        String sql = "INSERT OR REPLACE INTO game_player(game_id, player_id, role) VALUES (?, ?, ?)";
        Jdbc.update(db.connection(), sql, gameId, localId, LOCAL);
        Jdbc.update(db.connection(), sql, gameId, remoteId, REMOTE);
    }

    public Optional<GameRecord> findById(long id) throws SQLException {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id, mode, status, started_at, ended_at, winner_role, remote_ip, port FROM game WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new GameRecord(rs.getLong("id"), rs.getString("mode"), rs.getString("status"),
                        rs.getString("started_at"), rs.getString("ended_at"), rs.getString("winner_role"),
                        rs.getString("remote_ip"), Jdbc.nullableInt(rs, "port")));
            }
        }
    }

    public void delete(long gameId) throws SQLException {
        Jdbc.update(db.connection(), "DELETE FROM game WHERE id = ?", gameId);
    }
}
