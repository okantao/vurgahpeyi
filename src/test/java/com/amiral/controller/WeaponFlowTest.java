package com.amiral.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amiral.db.AbilityRepository;
import com.amiral.db.Database;
import com.amiral.db.MoveRepository;
import com.amiral.model.Board;
import com.amiral.model.Coordinate;
import com.amiral.model.Fleets;
import com.amiral.model.Orientation;
import com.amiral.model.Ship;
import com.amiral.model.ShipType;
import com.amiral.model.ShotResult;
import com.amiral.model.Weapon;
import com.amiral.net.Message;
import com.amiral.net.MessageType;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Radar and torpedo between two controllers over the in-memory network. */
class WeaponFlowTest {

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
        server.startServer(9090, "TestServer");
        client.connect("127.0.0.1", 9090, "TestClient");
        net.pump();
        server.ready(Fleets.standard());
        client.ready(clientFleet());
        net.pump();
    }

    @AfterEach
    void tearDown() throws SQLException {
        serverDb.close();
        clientDb.close();
    }

    /** One ship per row F-J from column 1. */
    private static Board clientFleet() {
        Board b = new Board();
        b.placeShip(new Ship(ShipType.CARRIER, c("F1"), Orientation.H));
        b.placeShip(new Ship(ShipType.BATTLESHIP, c("G1"), Orientation.H));
        b.placeShip(new Ship(ShipType.CRUISER, c("H1"), Orientation.H));
        b.placeShip(new Ship(ShipType.SUBMARINE, c("I1"), Orientation.H));
        b.placeShip(new Ship(ShipType.DESTROYER, c("J1"), Orientation.H));
        return b;
    }

    private static Coordinate c(String s) {
        return Coordinate.parse(s);
    }

    private static <T extends AppEvent> List<T> events(List<AppEvent> all, Class<T> type) {
        return all.stream().filter(type::isInstance).map(type::cast).toList();
    }

    private static <T extends AppEvent> T last(List<AppEvent> all, Class<T> type) {
        List<T> list = events(all, type);
        return list.get(list.size() - 1);
    }

    @Test
    void radarRevealsShipsOnBothSides() throws SQLException {
        server.act(Weapon.RADAR, c("G2"));
        net.pump();
        AppEvent.RadarScan mine = last(serverEvents, AppEvent.RadarScan.class);
        assertTrue(mine.mine());
        assertEquals(16, mine.area().size());
        // Area F1-I4: carrier F1-F4, battleship G1-G4, cruiser H1-H3, submarine I1-I3.
        assertEquals(14, mine.shipCells().size());
        AppEvent.RadarScan theirs = last(clientEvents, AppEvent.RadarScan.class);
        assertFalse(theirs.mine());
        assertEquals(mine.shipCells(), theirs.shipCells());
        assertEquals(new AppEvent.AbilitiesChanged(2, 2, 3, 2), last(serverEvents, AppEvent.AbilitiesChanged.class));
        assertEquals(new AppEvent.AbilitiesChanged(3, 2, 2, 2), last(clientEvents, AppEvent.AbilitiesChanged.class));
        assertTrue(last(clientEvents, AppEvent.TurnChanged.class).yourTurn(), "radar used the server's turn");
        assertEquals(2, server.usesLeft(Weapon.RADAR));

        AbilityRepository.AbilityUse use = new AbilityRepository(serverDb).findByGame(server.currentGameId()).get(0);
        assertEquals("RADAR", use.ability());
        assertEquals("LOCAL", use.userRole());
        assertEquals(14, use.found());
        assertEquals("REMOTE", new AbilityRepository(clientDb).findByGame(client.currentGameId()).get(0).userRole());
        assertTrue(server.moves(server.currentGameId()).stream().anyMatch(m -> m.weapon().equals("RADAR")));
    }

    @Test
    void torpedoHitsAreaAndSinks() throws SQLException {
        server.act(Weapon.TORPEDO, c("I2"));
        net.pump();
        AppEvent.TorpedoStrike strike = last(serverEvents, AppEvent.TorpedoStrike.class);
        assertEquals(9, strike.outcomes().size());
        // Area H1-J3: cruiser H1-H3 sunk, submarine I1-I3 sunk, destroyer J1-J2 sunk.
        assertEquals(3, strike.outcomes().stream().filter(o -> o.result() == ShotResult.SUNK).count());
        assertTrue(strike.outcomes().stream().filter(o -> o.result() == ShotResult.SUNK)
                .allMatch(o -> !o.sunkCells().isEmpty()), "sunk ships carry their cells");
        assertEquals(8, strike.outcomes().stream().filter(o -> o.result().isHit()).count());
        assertEquals(9, new MoveRepository(serverDb).findByGame(server.currentGameId()).stream()
                .filter(m -> m.weapon().equals("TORPEDO")).count());
        assertEquals(9, new MoveRepository(clientDb).findByGame(client.currentGameId()).size());
        assertFalse(last(clientEvents, AppEvent.TorpedoStrike.class).mine());
    }

    @Test
    void limitsAreEnforcedLocally() {
        for (int i = 0; i < 3; i++) {
            server.act(Weapon.RADAR, c("A1"));
            net.pump();
            client.fire(Coordinate.of(5 + i, 10));
            net.pump();
        }
        int sent = net.server.sent.size();
        server.act(Weapon.RADAR, c("A1"));
        assertEquals(sent, net.server.sent.size(), "fourth radar is not sent");
        assertTrue(last(serverEvents, AppEvent.Error.class).message().contains("No Radar left"));
        assertEquals(0, server.usesLeft(Weapon.RADAR));
        server.fire(c("J5"));
        assertEquals(sent + 1, net.server.sent.size(), "turn kept: a normal shot still works");
    }

    @Test
    void opponentOverLimitIsRefused() {
        server.fire(c("A10"));
        net.pump();
        for (int i = 0; i < 3; i++) {
            client.act(Weapon.RADAR, c("E5"));
            net.pump();
            server.fire(Coordinate.of(0, i + 1));
            net.pump();
        }
        // A cheating client sends a fourth radar on its turn.
        server.onMessage(Message.radar("E5"));
        Message reply = net.server.lastSent();
        assertEquals(MessageType.ERROR, reply.type());
        assertEquals("NO_ABILITY_LEFT", reply.field(0));
    }

    @Test
    void actionOutOfTurnIsRefused() {
        server.onMessage(Message.torpedo("A1"));
        assertEquals("NOT_YOUR_TURN", net.server.lastSent().field(0));
    }

    @Test
    void refusedTorpedoGivesItBack() {
        server.act(Weapon.TORPEDO, c("A1"));
        server.onMessage(Message.error("NO_ABILITY_LEFT", "x"));
        assertEquals(2, server.usesLeft(Weapon.TORPEDO));
        assertTrue(last(serverEvents, AppEvent.TurnChanged.class).yourTurn());
    }

    @Test
    void radarBeforeStartIsRefused() throws SQLException {
        Database db = Database.open(Database.MEMORY_URL);
        db.initSchema();
        FakeNetwork other = new FakeNetwork();
        GameController lonely = new GameController(db, other.server, Runnable::run, Runnable::run);
        lonely.startServer(9091, "Alone");
        Database joinerDb = Database.open(Database.MEMORY_URL);
        joinerDb.initSchema();
        GameController joiner = new GameController(joinerDb, other.client, Runnable::run, Runnable::run);
        joiner.connect("127.0.0.1", 9091, "Joiner");
        lonely.onMessage(Message.torpedo("A1"));
        assertEquals("GAME_NOT_STARTED", other.server.lastSent().field(0));
        db.close();
        joinerDb.close();
    }
}
