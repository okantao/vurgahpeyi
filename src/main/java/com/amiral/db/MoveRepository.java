package com.amiral.db;

import com.amiral.model.Coordinate;
import com.amiral.model.ShotResult;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Access to the {@code move} table. */
public class MoveRepository {

    public record Move(long id, long gameId, int turnNo, String shooter, String coord, String result,
            String createdAt) {
    }

    private final Database db;

    public MoveRepository(Database db) {
        this.db = db;
    }

    public void save(long gameId, int turnNo, String shooter, Coordinate c, ShotResult r) throws SQLException {
        Jdbc.insert(db.connection(),
                "INSERT INTO move(game_id, turn_no, shooter, coord, result) VALUES (?, ?, ?, ?, ?)",
                gameId, turnNo, shooter, c.toString(), r.name());
    }

    public List<Move> findByGame(long gameId) throws SQLException {
        List<Move> list = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id, game_id, turn_no, shooter, coord, result, created_at FROM move "
                        + "WHERE game_id = ? ORDER BY turn_no, id")) {
            ps.setLong(1, gameId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Move(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4), rs.getString(5),
                            rs.getString(6), rs.getString(7)));
                }
            }
        }
        return list;
    }
}
