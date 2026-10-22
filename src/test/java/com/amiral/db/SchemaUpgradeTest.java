package com.amiral.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amiral.model.Coordinate;
import com.amiral.model.ShotResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Radar/torpedo tables, the ability trigger and the upgrade of a v1.0 database. */
class SchemaUpgradeTest {

    private static Database fresh() throws SQLException {
        Database db = Database.open(Database.MEMORY_URL);
        db.initSchema();
        return db;
    }

    @Test
    void splitterKeepsTriggerBodyTogether() {
        List<String> parts = Database.splitStatements("""
                CREATE TABLE a (x INTEGER);
                -- comment only
                CREATE TRIGGER t BEFORE INSERT ON a BEGIN
                  SELECT RAISE(ABORT, 'no');
                END;
                CREATE INDEX i ON a(x);
                """);
        assertEquals(3, parts.size());
        assertTrue(parts.get(1).contains("RAISE(ABORT, 'no');") && parts.get(1).endsWith("END"));
    }

    @Test
    void aiModeAndWeaponsAreStored() throws SQLException {
        try (Database db = fresh()) {
            GameRepository games = new GameRepository(db);
            long g = games.createGame("AI", null, null);
            new MoveRepository(db).save(g, 1, "LOCAL", Coordinate.parse("A1"), ShotResult.HIT, "TORPEDO");
            assertEquals("TORPEDO", new MoveRepository(db).findByGame(g).get(0).weapon());
            assertThrows(SQLException.class,
                    () -> new MoveRepository(db).save(g, 2, "LOCAL", Coordinate.parse("A2"), ShotResult.HIT, "LASER"));
            AbilityRepository abilities = new AbilityRepository(db);
            abilities.save(g, 2, "LOCAL", "RADAR", "E5", 3);
            assertEquals(3, abilities.findByGame(g).get(0).found());
            assertThrows(SQLException.class, () -> abilities.save(g, 3, "LOCAL", "NUKE", "E5", 0));
            assertThrows(SQLException.class, () -> abilities.save(g, 3, "LOCAL", "RADAR", "K5", 0));
        }
    }

    @Test
    void triggerLimitsRadarsAndTorpedoes() throws SQLException {
        try (Database db = fresh()) {
            long g = new GameRepository(db).createGame("SERVER", null, 9090);
            AbilityRepository abilities = new AbilityRepository(db);
            for (int i = 1; i <= 3; i++) {
                abilities.save(g, i, "LOCAL", "RADAR", "E5", 0);
            }
            SQLException e = assertThrows(SQLException.class, () -> abilities.save(g, 4, "LOCAL", "RADAR", "E5", 0));
            assertTrue(e.getMessage().contains("ability limit reached"), e.getMessage());
            abilities.save(g, 4, "REMOTE", "RADAR", "E5", 0);
            abilities.save(g, 5, "LOCAL", "TORPEDO", "E5", 0);
            abilities.save(g, 6, "LOCAL", "TORPEDO", "E5", 0);
            assertThrows(SQLException.class, () -> abilities.save(g, 7, "LOCAL", "TORPEDO", "E5", 0));
            assertEquals(6, abilities.findByGame(g).size());
        }
    }

    @Test
    void abilitiesAreDeletedWithTheGame() throws SQLException {
        try (Database db = fresh()) {
            GameRepository games = new GameRepository(db);
            long g = games.createGame("SERVER", null, 9090);
            new AbilityRepository(db).save(g, 1, "LOCAL", "RADAR", "E5", 0);
            games.delete(g);
            assertTrue(new AbilityRepository(db).findByGame(g).isEmpty());
        }
    }

    @Test
    void upgradesVersionOneDatabase(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("old.db");
        long oldGame;
        try (Database db = Database.open(url); Statement st = db.connection().createStatement();
                InputStream in = SchemaUpgradeTest.class.getResourceAsStream("/schema_v1.sql")) {
            for (String sql : Database.splitStatements(new String(in.readAllBytes(), StandardCharsets.UTF_8))) {
                st.execute(sql);
            }
            oldGame = new GameRepository(db).createGame("SERVER", "10.0.0.2", 9090);
            st.execute("INSERT INTO move(game_id, turn_no, shooter, coord, result) VALUES (" + oldGame
                    + ", 1, 'LOCAL', 'A1', 'HIT')");
            new GameRepository(db).finish(oldGame, "LOCAL");
            assertThrows(SQLException.class, () -> new GameRepository(db).createGame("AI", null, null));
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        try (Database db = Database.open(url)) {
            db.initSchema();
            GameRepository games = new GameRepository(db);
            assertEquals("FINISHED", games.findById(oldGame).orElseThrow().status(), "old rows kept");
            assertEquals("SHOT", new MoveRepository(db).findByGame(oldGame).get(0).weapon());
            long ai = games.createGame("AI", null, null);
            assertTrue(ai > oldGame);
            new AbilityRepository(db).save(ai, 1, "LOCAL", "RADAR", "A1", 0);
            assertFalse(db.migrate(), "second run changes nothing");
            assertThrows(SQLException.class, () -> new MoveRepository(db).save(9999, 1, "LOCAL",
                    Coordinate.parse("B1"), ShotResult.MISS), "foreign keys still enforced");
            games.delete(oldGame);
            assertTrue(new MoveRepository(db).findByGame(oldGame).isEmpty(), "cascade still works");
            try (Statement st = db.connection().createStatement();
                    ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE name = 'idx_game_status'")) {
                rs.next();
                assertEquals(1, rs.getInt(1), "index recreated after rebuild");
            }
        }
    }
}
