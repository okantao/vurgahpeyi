package com.amiral.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amiral.db.AbilityRepository;
import com.amiral.db.Database;
import com.amiral.db.GameRepository;
import com.amiral.model.ComputerPlayer;
import com.amiral.model.Coordinate;
import com.amiral.model.Fleets;
import com.amiral.model.GameState;
import com.amiral.model.Ship;
import com.amiral.model.Weapon;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Playing against the computer through the controller, with a hand-driven scheduler. */
class ComputerGameTest {

    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private final List<AppEvent> events = new ArrayList<>();
    private final List<ComputerGateway> gateways = new ArrayList<>();
    private Database db;
    private GameController controller;

    @BeforeEach
    void setUp() throws SQLException {
        db = Database.open(Database.MEMORY_URL);
        db.initSchema();
        controller = new GameController(db, new FakeNetwork().server, Runnable::run, Runnable::run);
        controller.setView(events::add);
        controller.setComputerFactory(d -> {
            ComputerGateway g = new ComputerGateway(d, new Random(7), (task, delay) -> tasks.add(task), 0);
            gateways.add(g);
            return g;
        });
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    private void pump() {
        Runnable r;
        while ((r = tasks.poll()) != null) {
            r.run();
        }
    }

    private boolean won() {
        return events.stream().anyMatch(e -> e instanceof AppEvent.GameOver g && g.won());
    }

    private boolean lost() {
        return events.stream().anyMatch(e -> e instanceof AppEvent.GameOver g && !g.won());
    }

    /** We know the computer's fleet (test only) and sink it shot by shot while it fires back. */
    private void sinkComputerFleet() {
        List<Coordinate> targets = new ArrayList<>();
        for (Ship s : gateways.get(gateways.size() - 1).fleet().ships()) {
            targets.addAll(s.cells());
        }
        for (Coordinate c : targets) {
            if (controller.gameState() != GameState.PLAYING) {
                break;
            }
            controller.fire(c);
            pump();
        }
    }

    @Test
    void computerJoinsAndIsReady() {
        controller.playComputer("Kaan", ComputerPlayer.Difficulty.NORMAL);
        assertEquals(ConnectionState.CONNECTED, controller.connectionState());
        assertEquals(GameMode.AI, controller.mode());
        assertEquals("Computer (Normal)", events.stream().filter(e -> e instanceof AppEvent.ClientConnected)
                .map(e -> ((AppEvent.ClientConnected) e).nickname()).findFirst().orElseThrow());
        pump();
        controller.ready(Fleets.standard());
        pump();
        assertEquals(GameState.PLAYING, controller.gameState());
        assertTrue(events.stream().anyMatch(e -> e instanceof AppEvent.GameStarted g && g.yourTurn()),
                "human moves first");
    }

    @Test
    void playerCanBeatTheComputer() throws SQLException {
        controller.playComputer("Kaan", ComputerPlayer.Difficulty.EASY);
        pump();
        controller.ready(Fleets.standard());
        pump();
        sinkComputerFleet();
        assertTrue(won() || lost());
        GameRepository.GameRecord g = new GameRepository(db).findById(controller.currentGameId()).orElseThrow();
        assertEquals("AI", g.mode());
        assertEquals("FINISHED", g.status());
        assertEquals("Computer (Easy)", controller.games(5).get(0).opponent());
        assertTrue(won(), "easy computer cannot sink 17 cells in 17 turns");
    }

    @Test
    void hardComputerUsesRadarAndTorpedoAndCanWin() throws SQLException {
        controller.playComputer("Kaan", ComputerPlayer.Difficulty.HARD);
        pump();
        controller.ready(Fleets.standard());
        pump();
        // We only shoot at row J and further water, so the computer wins.
        int i = 0;
        while (controller.gameState() == GameState.PLAYING) {
            Coordinate c = Coordinate.of(9 - i / 10, i % 10 + 1);
            i++;
            controller.fire(c);
            pump();
        }
        assertTrue(lost());
        long remoteAbilities = new AbilityRepository(db).findByGame(controller.currentGameId()).stream()
                .filter(a -> a.userRole().equals("REMOTE")).count();
        assertTrue(remoteAbilities > 0, "hard computer used radar or torpedo");
        assertTrue(events.stream().anyMatch(e -> e instanceof AppEvent.RadarScan r && !r.mine())
                || events.stream().anyMatch(e -> e instanceof AppEvent.TorpedoStrike t && !t.mine()));
    }

    @Test
    void weaponsWorkAgainstTheComputer() {
        controller.playComputer("Kaan", ComputerPlayer.Difficulty.NORMAL);
        pump();
        controller.ready(Fleets.standard());
        pump();
        controller.act(Weapon.RADAR, Coordinate.parse("E5"));
        pump();
        assertTrue(events.stream().anyMatch(e -> e instanceof AppEvent.RadarScan r && r.mine()));
        controller.act(Weapon.TORPEDO, Coordinate.parse("B2"));
        pump();
        assertTrue(events.stream().anyMatch(e -> e instanceof AppEvent.TorpedoStrike t && t.mine()));
        assertEquals(2, controller.usesLeft(Weapon.RADAR));
        assertEquals(1, controller.usesLeft(Weapon.TORPEDO));
    }

    @Test
    void chatGetsAnAnswer() {
        controller.playComputer("Kaan", ComputerPlayer.Difficulty.NORMAL);
        pump();
        controller.sendChat("merhaba");
        pump();
        assertTrue(events.stream().anyMatch(e -> e instanceof AppEvent.ChatReceived c && !c.local()));
    }

    @Test
    void newGameAgainstTheComputer() {
        controller.playComputer("Kaan", ComputerPlayer.Difficulty.EASY);
        pump();
        controller.ready(Fleets.standard());
        pump();
        sinkComputerFleet();
        long first = controller.currentGameId();
        controller.newGame();
        controller.ready(Fleets.standard());
        pump();
        assertEquals(GameState.PLAYING, controller.gameState());
        assertTrue(controller.currentGameId() > first);
    }

    @Test
    void endingTheGameAbortsIt() throws SQLException {
        controller.playComputer("Kaan", ComputerPlayer.Difficulty.NORMAL);
        pump();
        controller.ready(Fleets.standard());
        pump();
        assertTrue(controller.isGameInProgress());
        controller.stop();
        pump();
        assertEquals(ConnectionState.IDLE, controller.connectionState());
        assertEquals("ABORTED", new GameRepository(db).findById(controller.currentGameId()).orElseThrow().status());
        assertFalse(events.stream().anyMatch(e -> e instanceof AppEvent.Disconnected));
    }
}
