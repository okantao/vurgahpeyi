package com.amiral.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only statistics and history queries. */
public class StatsRepository {

    /** Totals over all games that have ended. Win rate is wins / (wins + losses), 0 when nothing finished. */
    public record StatsSummary(int games, int wins, int losses, int aborted, double winRate) {
    }

    /** One row of the history table. {@code result} is WIN, LOSS, ABORTED or ACTIVE. */
    public record GameSummary(long id, String mode, String startedAt, String endedAt, String remoteIp,
            String result, String localNickname, String remoteNickname) {
    }

    private final Database db;

    public StatsRepository(Database db) {
        this.db = db;
    }

    public StatsSummary summary() throws SQLException {
        String sql = """
                SELECT COUNT(*) AS games,
                       COALESCE(SUM(CASE WHEN status = 'FINISHED' AND winner_role = 'LOCAL'  THEN 1 ELSE 0 END), 0) AS wins,
                       COALESCE(SUM(CASE WHEN status = 'FINISHED' AND winner_role = 'REMOTE' THEN 1 ELSE 0 END), 0) AS losses,
                       COALESCE(SUM(CASE WHEN status = 'ABORTED' THEN 1 ELSE 0 END), 0) AS aborted
                FROM game WHERE status <> 'ACTIVE'
                """;
        try (Statement st = db.connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            int wins = rs.getInt("wins");
            int losses = rs.getInt("losses");
            double rate = wins + losses == 0 ? 0.0 : 100.0 * wins / (wins + losses);
            return new StatsSummary(rs.getInt("games"), wins, losses, rs.getInt("aborted"), rate);
        }
    }

    public Map<String, Integer> winsByRole() throws SQLException {
        Map<String, Integer> map = new LinkedHashMap<>();
        try (Statement st = db.connection().createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT winner_role, COUNT(*) FROM game WHERE status = 'FINISHED' GROUP BY winner_role")) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getInt(2));
            }
        }
        return map;
    }

    /** Latest games first, joined with player nicknames. */
    public List<GameSummary> recentGames(int limit) throws SQLException {
        String sql = """
                SELECT g.id, g.mode, g.started_at, g.ended_at, g.remote_ip,
                       CASE WHEN g.status = 'FINISHED' AND g.winner_role = 'LOCAL' THEN 'WIN'
                            WHEN g.status = 'FINISHED' THEN 'LOSS'
                            ELSE g.status END AS result,
                       pl.nickname AS local_nick, pr.nickname AS remote_nick
                FROM game g
                LEFT JOIN game_player gl ON gl.game_id = g.id AND gl.role = 'LOCAL'
                LEFT JOIN player pl      ON pl.id = gl.player_id
                LEFT JOIN game_player gr ON gr.game_id = g.id AND gr.role = 'REMOTE'
                LEFT JOIN player pr      ON pr.id = gr.player_id
                ORDER BY g.started_at DESC, g.id DESC
                LIMIT ?
                """;
        List<GameSummary> list = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new GameSummary(rs.getLong("id"), rs.getString("mode"), rs.getString("started_at"),
                            rs.getString("ended_at"), rs.getString("remote_ip"), rs.getString("result"),
                            rs.getString("local_nick"), rs.getString("remote_nick")));
                }
            }
        }
        return list;
    }

    /** Number of shots per shooter (LOCAL / REMOTE) in a game. */
    public Map<String, Long> shotsByShooter(long gameId) throws SQLException {
        return countByShooter("SELECT shooter, COUNT(*) FROM move WHERE game_id = ? GROUP BY shooter", gameId);
    }

    /** Number of HIT or SUNK shots per shooter in a game. */
    public Map<String, Long> hitCountByShooter(long gameId) throws SQLException {
        return countByShooter("SELECT shooter, COUNT(*) FROM move WHERE game_id = ? AND result IN ('HIT','SUNK') "
                + "GROUP BY shooter", gameId);
    }

    private Map<String, Long> countByShooter(String sql, long gameId) throws SQLException {
        Map<String, Long> map = new LinkedHashMap<>();
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setLong(1, gameId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString(1), rs.getLong(2));
                }
            }
        }
        return map;
    }
}
