package com.amiral.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amiral.db.ChatRepository;
import com.amiral.db.Database;
import com.amiral.db.GameRepository;
import com.amiral.db.MoveRepository;
import com.amiral.db.ShipPlacementRepository;
import com.amiral.model.Board;
import com.amiral.model.Coordinate;
import com.amiral.model.Fleets;
import com.amiral.model.GameState;
import com.amiral.model.Orientation;
import com.amiral.model.Ship;
import com.amiral.model.ShipType;
import com.amiral.model.ShotResult;
import com.amiral.net.Message;
import com.amiral.net.MessageType;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameControllerTest {

    private FakeNetwork net;
    private Database serverDb;
    private Database clientDb;
    private GameController server;
    private GameController client;
    private final List<AppEvent> serverEvents = new ArrayList<>();
    private final List<AppEvent> clientEvents = new ArrayList<>();

    @BeforeEach
    void setUp() throws SQLException {
        net = new FakeNetwork();
        serverDb = Database.open(Database.MEMORY_URL);
        serverDb.initSchema();
        clientDb = Database.open(Database.MEMORY_URL);
        clientDb.initSchema();
        server = new GameController(serverDb, net.server, Runnable::run, Runnable::run);
        client = new GameController(clientDb, net.client, Runnable::run, Runnable::run);
        server.setView(serverEvents::add);
        client.setView(clientEvents::add);
    }

    @AfterEach
    void tearDown() throws SQLException {
        serverDb.close();
        clientDb.close();
    }

    /** Client fleet: one ship per row F-J, starting at column 1. */
    private static Board clientFleet() {
        Board b = new Board();
        b.placeShip(new Ship(ShipType.CARRIER, c("F1"), Orientation.H));
        b.placeShip(new Ship(ShipType.BATTLESHIP, c("G1"), Orientation.H));
        b.placeShip(new Ship(ShipType.CRUISER, c("H1"), Orientation.H));
        b.placeShip(new Ship(ShipType.SUBMARINE, c("I1"), Orientation.H));
        b.placeShip(new Ship(ShipType.DESTROYER, c("J1"), Orientation.H));
        return b;
    }

    private static final String[] CLIENT_CELLS = {
        "F1", "F2", "F3", "F4", "F5", "G1", "G2", "G3", "G4", "H1", "H2", "H3", "I1", "I2", "I3", "J1", "J2"
    };

    private static Coordinate c(String s) {
        return Coordinate.parse(s);
    }

    /** A cell in rows F-J of the server board, which holds no ship. */
    private static Coordinate miss(int i) {
        return Coordinate.of(5 + i / 10, i % 10 + 1);
    }

    private void connectAndReady() {
        server.startServer(9090, "TestServer");
        client.connect("127.0.0.1", 9090, "TestClient");
        net.pump();
        server.ready(Fleets.standard());
        client.ready(clientFleet());
        net.pump();
    }

    private static <T extends AppEvent> List<T> events(List<AppEvent> all, Class<T> type) {
        List<T> list = new ArrayList<>();
        for (AppEvent e : all) {
            if (type.isInstance(e)) {
                list.add(type.cast(e));
            }
        }
        return list;
    }

    private static boolean has(List<AppEvent> all, Predicate<AppEvent> p) {
        return all.stream().anyMatch(p);
    }

    private static String lastStatus(List<AppEvent> all) {
        List<AppEvent.Status> s = events(all, AppEvent.Status.class);
        return s.get(s.size() - 1).text();
    }

    @Test
    void serverStartsAndClientJoins() {
        server.startServer(9090, "TestServer");
        assertEquals(ConnectionState.LISTENING, server.connectionState());
        assertTrue(has(serverEvents, e -> e instanceof AppEvent.Log l && l.message().startsWith("Server is opened!")));
        assertEquals(GameController.STATUS_WAITING, lastStatus(serverEvents));

        client.connect("127.0.0.1", 9090, "TestClient");
        net.pump();
        assertEquals(ConnectionState.CONNECTED, server.connectionState());
        assertEquals(ConnectionState.CONNECTED, client.connectionState());
        assertTrue(has(serverEvents, e -> e instanceof AppEvent.Log l && l.message().startsWith("TestClient wants to join")));
        assertEquals("TestServer", events(clientEvents, AppEvent.ClientConnected.class).get(0).nickname());
        assertEquals(GameController.STATUS_PLACING, lastStatus(clientEvents));
        assertTrue(server.currentGameId() != null && client.currentGameId() != null);
    }

    @Test
    void bothReadyStartsGameWithServerFirst() throws SQLException {
        connectAndReady();
        assertEquals(GameState.PLAYING, server.gameState());
        assertEquals(GameState.PLAYING, client.gameState());
        assertTrue(events(serverEvents, AppEvent.GameStarted.class).get(0).yourTurn());
        assertFalse(events(clientEvents, AppEvent.GameStarted.class).get(0).yourTurn());
        assertTrue(net.server.sent.stream().anyMatch(m -> m.type() == MessageType.START));
        assertEquals(ConnectionState.IN_GAME, server.connectionState());
        assertEquals(5, new ShipPlacementRepository(serverDb).findByGame(server.currentGameId()).size());
        assertEquals(5, new ShipPlacementRepository(clientDb).findByGame(client.currentGameId()).size());
    }

    @Test
    void readyNeedsOpponent() {
        server.ready(Fleets.standard());
        assertTrue(has(serverEvents, e -> e instanceof AppEvent.Error));
        assertNull(server.gameState());
    }

    @Test
    void fullGameServerWins() throws SQLException {
        connectAndReady();
        client.sendChat("İyi şanslar!");
        net.pump();
        // Every shot hits, so the server keeps the turn and the client's shots are ignored.
        for (int i = 0; i < CLIENT_CELLS.length; i++) {
            server.fire(c(CLIENT_CELLS[i]));
            net.pump();
            if (i < CLIENT_CELLS.length - 1) {
                assertEquals(GameController.STATUS_YOUR_TURN, lastStatus(serverEvents));
                client.fire(miss(i));
                net.pump();
            }
        }
        assertEquals(GameState.FINISHED, server.gameState());
        assertEquals(GameState.FINISHED, client.gameState());
        assertTrue(events(serverEvents, AppEvent.GameOver.class).get(0).won());
        assertFalse(events(clientEvents, AppEvent.GameOver.class).get(0).won());
        assertEquals(GameController.STATUS_WON, lastStatus(serverEvents));
        assertEquals(GameController.STATUS_LOST, lastStatus(clientEvents));

        AppEvent.ShotFired last = events(serverEvents, AppEvent.ShotFired.class).get(16);
        assertEquals(ShotResult.SUNK, last.result());
        assertEquals(ShipType.DESTROYER, last.sunkType());
        assertEquals(List.of(c("J1"), c("J2")), last.sunkCells());
        assertEquals(5, events(clientEvents, AppEvent.ShotReceived.class).stream()
                .filter(s -> s.result() == ShotResult.SUNK).count());

        GameRepository.GameRecord sg = new GameRepository(serverDb).findById(server.currentGameId()).orElseThrow();
        assertEquals("FINISHED", sg.status());
        assertEquals("LOCAL", sg.winnerRole());
        GameRepository.GameRecord cg = new GameRepository(clientDb).findById(client.currentGameId()).orElseThrow();
        assertEquals("REMOTE", cg.winnerRole());
        assertEquals(17, new MoveRepository(serverDb).findByGame(server.currentGameId()).size());
        assertEquals(17, new MoveRepository(clientDb).findByGame(client.currentGameId()).size());
        assertEquals("İyi şanslar!", new ChatRepository(serverDb).findByGame(server.currentGameId()).get(0).body());

        HistoryProvider.Stats stats = server.stats();
        assertEquals(1, stats.wins());
        assertEquals(100.0, stats.winRate());
        assertEquals("WIN", server.games(10).get(0).result());
        assertEquals("TestClient", server.games(10).get(0).opponent());
        assertEquals(17, server.moves(server.currentGameId()).size());
        assertEquals(1, client.stats().losses());
    }

    @Test
    void outOfTurnClickIsIgnored() {
        connectAndReady();
        int before = net.client.sent.size();
        client.fire(c("A1"));
        assertEquals(before, net.client.sent.size(), "no SHOOT sent");
        server.fire(c("F1"));
        server.fire(c("F2"));
        assertEquals(1, net.server.sent.stream().filter(m -> m.type() == MessageType.SHOOT).count());
    }

    @Test
    void shootOutOfTurnIsRefused() {
        connectAndReady();
        server.onMessage(Message.shoot("A1"));
        Message reply = net.server.lastSent();
        assertEquals(MessageType.ERROR, reply.type());
        assertEquals("NOT_YOUR_TURN", reply.field(0));
    }

    @Test
    void invalidCoordinateIsRefused() {
        connectAndReady();
        client.onMessage(Message.shoot("K11"));
        assertEquals("INVALID_COORD", net.client.lastSent().field(0));
    }

    @Test
    void hitKeepsTheTurnAndMissPassesIt() {
        connectAndReady();
        server.fire(c("F1"));
        net.pump();
        assertEquals(GameController.STATUS_YOUR_TURN, lastStatus(serverEvents), "hit: shoot again");
        assertEquals(GameController.STATUS_ENEMY_TURN, lastStatus(clientEvents));

        server.fire(c("A10"));
        net.pump();
        assertEquals(GameController.STATUS_ENEMY_TURN, lastStatus(serverEvents));
        assertEquals(GameController.STATUS_YOUR_TURN, lastStatus(clientEvents));

        client.fire(c("A1"));
        net.pump();
        assertEquals(GameController.STATUS_YOUR_TURN, lastStatus(clientEvents), "client hit A1");
        client.fire(c("J10"));
        net.pump();
        assertEquals(GameController.STATUS_YOUR_TURN, lastStatus(serverEvents));
        assertEquals(GameController.STATUS_ENEMY_TURN, lastStatus(clientEvents));
    }

    @Test
    void repeatedShotIsRefusedAndTurnStays() {
        connectAndReady();
        server.fire(c("A10"));
        net.pump();
        client.fire(c("J10"));
        net.pump();
        // A cheating server shoots A10 again: the client refuses, the server keeps its turn.
        server.onMessage(Message.of(MessageType.ERROR, "ALREADY_SHOT", "x"));
        client.onMessage(Message.shoot("A10"));
        assertEquals("ALREADY_SHOT", net.client.lastSent().field(0));

        // Our own rejected shot gives the turn back.
        server.fire(c("B10"));
        assertFalse(events(serverEvents, AppEvent.TurnChanged.class).isEmpty());
        server.onMessage(Message.error("NOT_YOUR_TURN", "nope"));
        List<AppEvent.TurnChanged> turns = events(serverEvents, AppEvent.TurnChanged.class);
        assertTrue(turns.get(turns.size() - 1).yourTurn());
        assertEquals(GameController.STATUS_YOUR_TURN, lastStatus(serverEvents));
    }

    @Test
    void shotBeforeStartIsRefused() {
        server.startServer(9090, "TestServer");
        client.connect("127.0.0.1", 9090, "TestClient");
        net.pump();
        server.onMessage(Message.shoot("A1"));
        assertEquals("GAME_NOT_STARTED", net.server.lastSent().field(0));
    }

    @Test
    void invalidFleetIsRefused() {
        server.startServer(9090, "TestServer");
        client.connect("127.0.0.1", 9090, "TestClient");
        net.pump();
        server.onMessage(Message.ready("CARRIER:A1H;BATTLESHIP:A2H"));
        assertEquals("INVALID_FLEET", net.server.lastSent().field(0));
        server.onMessage(Message.ready("garbage"));
        assertEquals(GameState.SETUP, server.gameState());
    }

    @Test
    void opponentLeavingAbortsTheGame() throws SQLException {
        connectAndReady();
        server.fire(c("F1"));
        net.pump();
        client.stop();
        net.pump();
        assertEquals(ConnectionState.IDLE, client.connectionState());
        assertEquals(ConnectionState.LISTENING, server.connectionState(), "server keeps listening");
        assertTrue(events(serverEvents, AppEvent.Disconnected.class).get(0).opponentLeft());
        assertTrue(has(serverEvents, e -> e instanceof AppEvent.Log l && l.message().equals("Opponent has left the game.")));
        assertEquals("ABORTED", new GameRepository(serverDb).findById(server.currentGameId()).orElseThrow().status());
        assertEquals("ABORTED", new GameRepository(clientDb).findById(client.currentGameId()).orElseThrow().status());
        assertTrue(events(clientEvents, AppEvent.Disconnected.class).isEmpty(), "own disconnect is not a dialog");
    }

    @Test
    void lostConnectionAbortsTheGame() throws SQLException {
        connectAndReady();
        net.server.cut();
        assertEquals("ABORTED", new GameRepository(serverDb).findById(server.currentGameId()).orElseThrow().status());
        assertEquals(GameController.STATUS_DISCONNECTED, lastStatus(clientEvents));
        assertFalse(server.isGameInProgress());
    }

    @Test
    void stopServerDuringGame() throws SQLException {
        connectAndReady();
        assertTrue(server.isGameInProgress());
        server.stop();
        net.pump();
        assertEquals(ConnectionState.IDLE, server.connectionState());
        assertEquals(ConnectionState.IDLE, client.connectionState());
        assertEquals("ABORTED", new GameRepository(serverDb).findById(server.currentGameId()).orElseThrow().status());
        assertEquals("ABORTED", new GameRepository(clientDb).findById(client.currentGameId()).orElseThrow().status());
        assertTrue(events(clientEvents, AppEvent.Disconnected.class).get(0).opponentLeft());
    }

    @Test
    void portInUseIsReported() {
        server.startServer(FakeNetwork.BUSY_PORT, "TestServer");
        assertEquals(ConnectionState.IDLE, server.connectionState());
        AppEvent.Error err = events(serverEvents, AppEvent.Error.class).get(0);
        assertTrue(err.message().startsWith("Port is in use"));
        assertTrue(err.dialog());
    }

    @Test
    void connectFailureIsReported() {
        client.connect("127.0.0.1", 9090, "TestClient");
        assertEquals(ConnectionState.IDLE, client.connectionState());
        assertTrue(events(clientEvents, AppEvent.Error.class).get(0).dialog());
    }

    @Test
    void chatNeedsConnectionAndValidText() {
        client.sendChat("hello");
        assertTrue(has(clientEvents, e -> e instanceof AppEvent.Error));
        server.startServer(9090, "TestServer");
        client.connect("127.0.0.1", 9090, "TestClient");
        net.pump();
        client.sendChat("   ");
        client.sendChat("x".repeat(501));
        client.sendChat("Merhaba ğüşıöç");
        net.pump();
        List<AppEvent.ChatReceived> got = events(serverEvents, AppEvent.ChatReceived.class);
        assertEquals(1, got.size());
        assertEquals("Merhaba ğüşıöç", got.get(0).body());
        assertEquals("TestClient", got.get(0).sender());
        assertTrue(events(clientEvents, AppEvent.ChatReceived.class).get(0).local());
    }

    @Test
    void newRoundAfterGameOver() {
        connectAndReady();
        for (int i = 0; i < CLIENT_CELLS.length; i++) {
            server.fire(c(CLIENT_CELLS[i]));
            net.pump();
            if (i < CLIENT_CELLS.length - 1) {
                client.fire(miss(i));
                net.pump();
            }
        }
        long firstGame = server.currentGameId();
        server.newGame();
        assertEquals(GameState.SETUP, server.gameState());
        assertTrue(server.currentGameId() > firstGame);
        server.ready(Fleets.standard());
        net.pump();
        assertEquals(GameState.SETUP, client.gameState(), "client started a new round on READY");
        assertTrue(has(clientEvents, e -> e instanceof AppEvent.RoundReset));
        client.ready(clientFleet());
        net.pump();
        assertEquals(GameState.PLAYING, server.gameState());
        assertEquals(2, server.games(10).size());
    }

    @Test
    void toLocalFormatsDatabaseTime() {
        assertNull(GameController.toLocal(null));
        assertEquals("bad", GameController.toLocal("bad"));
        assertTrue(GameController.toLocal("2026-01-02 03:04:05").matches("\\d\\d/\\d\\d/2026 \\d\\d:\\d\\d:05"));
    }
}
