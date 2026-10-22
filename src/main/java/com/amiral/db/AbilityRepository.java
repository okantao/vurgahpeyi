package com.amiral.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Access to the {@code ability_use} table (radar scans and torpedoes). A trigger enforces 3 radars / 2 torpedoes. */
public class AbilityRepository {

    public record AbilityUse(long id, long gameId, int turnNo, String userRole, String ability, String coord,
            int found, String createdAt) {
    }

    private final Database db;

    public AbilityRepository(Database db) {
        this.db = db;
    }

    /**
     * @param found ship cells found (radar) or cells hit (torpedo)
     */
    public void save(long gameId, int turnNo, String userRole, String ability, String coord, int found)
            throws SQLException {
        Jdbc.insert(db.connection(),
                "INSERT INTO ability_use(game_id, turn_no, user_role, ability, coord, found) VALUES (?, ?, ?, ?, ?, ?)",
                gameId, turnNo, userRole, ability, coord, found);
    }

    public List<AbilityUse> findByGame(long gameId) throws SQLException {
        List<AbilityUse> list = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id, game_id, turn_no, user_role, ability, coord, found, created_at FROM ability_use "
                        + "WHERE game_id = ? ORDER BY turn_no, id")) {
            ps.setLong(1, gameId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new AbilityUse(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4),
                            rs.getString(5), rs.getString(6), rs.getInt(7), rs.getString(8)));
                }
            }
        }
        return list;
    }
}
