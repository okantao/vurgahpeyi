package com.amiral.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.amiral.model.Coordinate;
import com.amiral.model.ShotResult;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatsRepositoryTest {

    private Database db;
    private GameRepository games;
    private StatsRepository stats;
    private long lastGame;

    @BeforeEach
    void setUp() throws SQLException {
        db = Database.open(Database.MEMORY_URL);
        db.initSchema();
        games = new GameRepository(db);
        stats = new StatsRepository(db);
        PlayerRepository players = new PlayerRepository(db);
        long me = players.upsert("Me");
        long you = players.upsert("You");

        long g1 = games.createGame("SERVER", "10.0.0.2", 9090);
        games.linkPlayers(g1, me, you);
        games.finish(g1, "LOCAL");
        long g2 = games.createGame("CLIENT", "10.0.0.3", 9090);
        games.finish(g2, "LOCAL");
        long g3 = games.createGame("SERVER", null, 9091);
        games.finish(g3, "REMOTE");
        long g4 = games.createGame("SERVER", null, 9092);
        games.abort(g4);
        lastGame = games.createGame("CLIENT", "10.0.0.9", 9090);

        MoveRepository moves = new MoveRepository(db);
        moves.save(g1, 1, "LOCAL", Coordinate.parse("A1"), ShotResult.HIT);
        moves.save(g1, 2, "REMOTE", Coordinate.parse("A1"), ShotResult.MISS);
        moves.save(g1, 3, "LOCAL", Coordinate.parse("A2"), ShotResult.SUNK);
        moves.save(g1, 4, "REMOTE", Coordinate.parse("B1"), ShotResult.MISS);
        moves.save(g1, 5, "LOCAL", Coordinate.parse("A3"), ShotResult.MISS);
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    @Test
    void summaryCountsEndedGames() throws SQLException {
        StatsRepository.StatsSummary s = stats.summary();
        assertEquals(4, s.games(), "active game not counted");
        assertEquals(2, s.wins());
        assertEquals(1, s.losses());
        assertEquals(1, s.aborted());
        assertEquals(66.67, s.winRate(), 0.01);
    }

    @Test
    void emptyDatabaseHasZeroRate() throws SQLException {
        try (Database empty = Database.open(Database.MEMORY_URL)) {
            empty.initSchema();
            StatsRepository.StatsSummary s = new StatsRepository(empty).summary();
            assertEquals(0, s.games());
            assertEquals(0.0, s.winRate());
        }
    }

    @Test
    void winsByRole() throws SQLException {
        assertEquals(Map.of("LOCAL", 2, "REMOTE", 1), stats.winsByRole());
    }

    @Test
    void recentGamesNewestFirst() throws SQLException {
        List<StatsRepository.GameSummary> list = stats.recentGames(10);
        assertEquals(5, list.size());
        assertEquals(lastGame, list.get(0).id());
        assertEquals("ACTIVE", list.get(0).result());
        assertEquals("ABORTED", list.get(1).result());
        assertEquals("LOSS", list.get(2).result());
        StatsRepository.GameSummary first = list.get(4);
        assertEquals("WIN", first.result());
        assertEquals("Me", first.localNickname());
        assertEquals("You", first.remoteNickname());
        assertNull(list.get(1).localNickname());
        assertEquals(2, stats.recentGames(2).size());
    }

    @Test
    void shotAndHitCounts() throws SQLException {
        long g1 = stats.recentGames(10).get(4).id();
        assertEquals(Map.of("LOCAL", 3L, "REMOTE", 2L), stats.shotsByShooter(g1));
        assertEquals(Map.of("LOCAL", 2L), stats.hitCountByShooter(g1));
    }
}
