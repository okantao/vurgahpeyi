package com.amiral.db;

import com.amiral.model.Ship;
import com.amiral.model.ShipType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Access to the {@code ship_placement} table (local player's fleet only). */
public class ShipPlacementRepository {

    public record ShipPlacement(long id, long gameId, String shipType, String startCoord, String orientation) {
    }

    private final Database db;

    public ShipPlacementRepository(Database db) {
        this.db = db;
    }

    public void saveFleet(long gameId, Map<ShipType, Ship> fleet) throws SQLException {
        String sql = "INSERT INTO ship_placement(game_id, ship_type, start_coord, orientation) VALUES (?, ?, ?, ?)";
        boolean auto = db.connection().getAutoCommit();
        db.connection().setAutoCommit(false);
        try {
            for (Ship s : fleet.values()) {
                Jdbc.update(db.connection(), sql, gameId, s.type().name(), s.start().toString(),
                        s.orientation().name());
            }
            db.connection().commit();
        } catch (SQLException e) {
            db.connection().rollback();
            throw e;
        } finally {
            db.connection().setAutoCommit(auto);
        }
    }

    /** Low-level insert used for single rows (and constraint tests). */
    public long save(long gameId, String shipType, String startCoord, String orientation) throws SQLException {
        return Jdbc.insert(db.connection(),
                "INSERT INTO ship_placement(game_id, ship_type, start_coord, orientation) VALUES (?, ?, ?, ?)",
                gameId, shipType, startCoord, orientation);
    }

    public List<ShipPlacement> findByGame(long gameId) throws SQLException {
        List<ShipPlacement> list = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id, game_id, ship_type, start_coord, orientation FROM ship_placement WHERE game_id = ? ORDER BY id")) {
            ps.setLong(1, gameId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ShipPlacement(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                            rs.getString(5)));
                }
            }
        }
        return list;
    }
}
