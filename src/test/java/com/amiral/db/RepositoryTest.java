package com.amiral.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amiral.model.Coordinate;
import com.amiral.model.RandomFleetGenerator;
import com.amiral.model.ShotResult;
import java.sql.SQLException;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepositoryTest {

    private Database db;
    private PlayerRepository players;
    private GameRepository games;
    private ShipPlacementRepository placements;
    private MoveRepository moves;
    private ChatRepository chats;
    private LogRepository logs;

    @BeforeEach
    void setUp() throws SQLException {
        db = Database.open(Database.MEMORY_URL);
        db.initSchema();
        players = new PlayerRepository(db);
        games = new GameRepository(db);
        placements = new ShipPlacementRepository(db);
        moves = new MoveRepository(db);
        chats = new ChatRepository(db);
        logs = new LogRepository(db);
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    @Test
    void playerUpsertIsIdempotent() throws SQLException {
        long id = players.upsert("Kaan");
        assertEquals(id, players.upsert("Kaan"));
        assertEquals("Kaan", players.findByNickname("Kaan").orElseThrow().nickname());
        assertTrue(players.findByNickname("Nobody").isEmpty());
        assertThrows(SQLException.class, () -> players.upsert("x".repeat(21)), "CHECK length");
    }

    @Test
    void gameLifecycle() throws SQLException {
        long id = games.createGame("SERVER", null, 9090);
        GameRepository.GameRecord g = games.findById(id).orElseThrow();
        assertEquals("ACTIVE", g.status());
        assertEquals(9090, g.port());
        assertNotNull(g.startedAt());
        assertNull(g.endedAt());

        games.updateRemoteIp(id, "192.168.1.5");
        games.finish(id, GameRepository.LOCAL);
        g = games.findById(id).orElseThrow();
        assertEquals("FINISHED", g.status());
        assertEquals("LOCAL", g.winnerRole());
        assertEquals("192.168.1.5", g.remoteIp());
        assertNotNull(g.endedAt());

        games.abort(id);
        assertEquals("FINISHED", games.findById(id).orElseThrow().status(), "finished game is not aborted");
        assertTrue(games.findById(999).isEmpty());
    }

    @Test
    void abortSetsStatus() throws SQLException {
        long id = games.createGame("CLIENT", "127.0.0.1", 9090);
        games.abort(id);
        GameRepository.GameRecord g = games.findById(id).orElseThrow();
        assertEquals("ABORTED", g.status());
        assertNull(g.winnerRole());
    }

    @Test
    void gameChecks() {
        assertThrows(SQLException.class, () -> games.createGame("PEER", null, 9090));
        assertThrows(SQLException.class, () -> games.createGame("SERVER", null, 80));
    }

    @Test
    void linkPlayersNeedsExistingPlayers() throws SQLException {
        long g = games.createGame("SERVER", null, 9090);
        long a = players.upsert("A");
        long b = players.upsert("B");
        games.linkPlayers(g, a, b);
        assertThrows(SQLException.class, () -> games.linkPlayers(g, a, 12345), "foreign key");
    }

    @Test
    void fleetIsSavedAndRead() throws SQLException {
        long g = games.createGame("SERVER", null, 9090);
        placements.saveFleet(g, RandomFleetGenerator.generate(new Random(1)).fleet());
        assertEquals(5, placements.findByGame(g).size());
        assertThrows(SQLException.class, () -> placements.save(g, "CARRIER", "A1", "H"), "UNIQUE ship type");
        assertEquals(5, placements.findByGame(g).size(), "failed batch did not add rows");
    }

    @Test
    void placementChecks() throws SQLException {
        long g = games.createGame("SERVER", null, 9090);
        assertThrows(SQLException.class, () -> placements.save(g, "CARRIER", "A1", "X"), "orientation CHECK");
        assertThrows(SQLException.class, () -> placements.save(g, "CARRIER", "K1", "H"), "coord CHECK");
        assertThrows(SQLException.class, () -> placements.save(g, "CARRIER", "A11", "H"), "coord CHECK");
        assertThrows(SQLException.class, () -> placements.save(g, "BOAT", "A1", "H"), "type CHECK");
        assertThrows(SQLException.class, () -> placements.save(777, "CARRIER", "A1", "H"), "foreign key");
        placements.save(g, "DESTROYER", "J10", "H");
        placements.save(g, "CARRIER", "A10", "V");
        assertEquals(2, placements.findByGame(g).size());
    }

    @Test
    void movesAreUniquePerShooterAndCell() throws SQLException {
        long g = games.createGame("SERVER", null, 9090);
        moves.save(g, 1, "LOCAL", Coordinate.parse("A1"), ShotResult.MISS);
        moves.save(g, 2, "REMOTE", Coordinate.parse("A1"), ShotResult.HIT);
        assertThrows(SQLException.class,
                () -> moves.save(g, 3, "LOCAL", Coordinate.parse("A1"), ShotResult.HIT), "UNIQUE");
        assertThrows(SQLException.class,
                () -> moves.save(g, 3, "LOCAL", Coordinate.parse("B1"), ShotResult.ALREADY_SHOT), "result CHECK");
        assertThrows(SQLException.class,
                () -> moves.save(g, 0, "LOCAL", Coordinate.parse("B1"), ShotResult.HIT), "turn CHECK");
        assertEquals(2, moves.findByGame(g).size());
        assertEquals("A1", moves.findByGame(g).get(0).coord());
    }

    @Test
    void chatKeepsTurkishText() throws SQLException {
        long g = games.createGame("SERVER", null, 9090);
        chats.save(g, "LOCAL", "Merhaba, şöyle güzel ığdır çay");
        chats.save(g, "REMOTE", "hi");
        assertEquals("Merhaba, şöyle güzel ığdır çay", chats.findByGame(g).get(0).body());
        assertThrows(SQLException.class, () -> chats.save(g, "LOCAL", ""), "length CHECK");
        assertThrows(SQLException.class, () -> chats.save(g, "LOCAL", "x".repeat(501)), "length CHECK");
        assertThrows(SQLException.class, () -> chats.save(g, "ME", "x"), "sender CHECK");
        chats.save(null, "LOCAL", "no game");
    }

    @Test
    void deleteCascades() throws SQLException {
        long g = games.createGame("SERVER", null, 9090);
        placements.saveFleet(g, RandomFleetGenerator.generate(new Random(2)).fleet());
        moves.save(g, 1, "LOCAL", Coordinate.parse("C3"), ShotResult.MISS);
        chats.save(g, "LOCAL", "bye");
        logs.info(g, "something");
        games.delete(g);
        assertTrue(placements.findByGame(g).isEmpty());
        assertTrue(moves.findByGame(g).isEmpty());
        assertTrue(chats.findByGame(g).isEmpty());
        assertNull(logs.recent(1).get(0).gameId(), "log keeps the row, game id set to NULL");
    }

    @Test
    void logLevels() throws SQLException {
        logs.info(null, "a");
        logs.warn(null, "b");
        logs.error(null, "c");
        assertEquals("ERROR", logs.recent(3).get(0).level());
        assertEquals("WARN", logs.recent(3).get(1).level());
        assertEquals("a", logs.recent(3).get(2).message());
    }
}
