package com.amiral.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.amiral.db.Database;
import com.amiral.db.GameRepository;
import com.amiral.model.Board;
import com.amiral.model.Coordinate;
import com.amiral.model.Fleets;
import com.amiral.model.GameState;
import java.io.IOException;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A full game between two controllers over real loopback sockets. */
class SocketGameIT {

    private Database serverDb;
    private Database clientDb;
    private GameController server;
    private GameController client;
    private final List<AppEvent> serverEvents = new CopyOnWriteArrayList<>();
    private final List<AppEvent> clientEvents = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws SQLException {
        serverDb = Database.open(Database.MEMORY_URL);
        serverDb.initSchema();
        clientDb = Database.open(Database.MEMORY_URL);
        clientDb.initSchema();
        server = new GameController(serverDb, new SocketGateway(), Runnable::run, Executors.newSingleThreadExecutor());
        client = new GameController(clientDb, new SocketGateway(), Runnable::run, Executors.newSingleThreadExecutor());
        server.setView(serverEvents::add);
        client.setView(clientEvents::add);
    }

    @AfterEach
    void tearDown() throws SQLException {
        client.shutdown();
        server.shutdown();
        serverDb.close();
        clientDb.close();
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static void waitFor(String what, BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        fail("Timed out waiting for " + what);
    }

    @Test
    void clientWinsOverTcp() throws Exception {
        int port = freePort();
        server.startServer(port, "TestServer");
        client.connect("127.0.0.1", port, "TestClient");
        waitFor("connection", () -> server.connectionState() == ConnectionState.CONNECTED
                && client.connectionState() == ConnectionState.CONNECTED);

        Board serverFleet = Fleets.standard();
        server.ready(serverFleet);
        client.ready(Fleets.standard());
        waitFor("start", () -> server.gameState() == GameState.PLAYING && client.gameState() == GameState.PLAYING);

        String[] targets = Fleets.standardCells();
        for (int i = 0; i < targets.length; i++) {
            Coordinate miss = Coordinate.of(5 + i / 10, i % 10 + 1);
            int shots = i;
            server.fire(miss);
            waitFor("server shot " + i, () -> countShots(serverEvents) == shots + 1);
            client.fire(Coordinate.parse(targets[i]));
            waitFor("client shot " + i, () -> countShots(clientEvents) == shots + 1);
        }
        waitFor("game over", () -> server.gameState() == GameState.FINISHED
                && client.gameState() == GameState.FINISHED);
        assertTrue(clientEvents.stream().anyMatch(e -> e instanceof AppEvent.GameOver g && g.won()));
        assertTrue(serverEvents.stream().anyMatch(e -> e instanceof AppEvent.GameOver g && !g.won()));
        assertEquals("REMOTE", new GameRepository(serverDb).findById(server.currentGameId()).orElseThrow().winnerRole());
        assertEquals("LOCAL", new GameRepository(clientDb).findById(client.currentGameId()).orElseThrow().winnerRole());

        client.sendChat("gg");
        waitFor("chat", () -> serverEvents.stream().anyMatch(e -> e instanceof AppEvent.ChatReceived c && !c.local()));

        client.stop();
        waitFor("disconnect", () -> server.connectionState() == ConnectionState.LISTENING);
    }

    private static long countShots(List<AppEvent> events) {
        return events.stream().filter(e -> e instanceof AppEvent.ShotFired).count();
    }
}
