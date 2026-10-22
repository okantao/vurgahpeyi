package com.amiral.controller;

import com.amiral.model.Board;
import com.amiral.model.ComputerPlayer;
import com.amiral.model.Coordinate;
import com.amiral.model.GameEngine;
import com.amiral.model.GameRuleException;
import com.amiral.model.GameState;
import com.amiral.model.RandomFleetGenerator;
import com.amiral.model.ShipType;
import com.amiral.model.ShotOutcome;
import com.amiral.model.ShotResult;
import com.amiral.model.Weapon;
import com.amiral.net.Message;
import com.amiral.net.NetworkListener;
import com.amiral.net.ProtocolCodec;
import com.amiral.net.ProtocolException;
import com.amiral.util.AppLogger;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * The computer opponent. It talks the same protocol as a network peer, so the controller treats it like a
 * client that joined our server: it keeps its own {@link GameEngine} and fleet and answers with RESULT,
 * RADAR_RESULT and TORPEDO_RESULT, then plays its own turn after a short "thinking" pause.
 */
public class ComputerGateway implements NetworkGateway {

    public static final long THINK_MILLIS = 900;
    private static final Logger LOG = AppLogger.get(ComputerGateway.class);
    private static final String[] CHAT_REPLIES = {
        "Good luck, admiral!", "My radar is watching you.", "Is that all you have?", "Nice try!",
        "Torpedoes ready.", "You will not find my carrier."
    };

    private final ComputerPlayer.Difficulty difficulty;
    private final Random random;
    private final Scheduler scheduler;
    private final long thinkMillis;
    private volatile NetworkListener listener;
    private volatile boolean closed = true;
    private GameEngine engine;
    private ComputerPlayer ai;
    private Board fleet;

    public ComputerGateway(ComputerPlayer.Difficulty difficulty) {
        this(difficulty, new Random(), defaultScheduler(), THINK_MILLIS);
    }

    public ComputerGateway(ComputerPlayer.Difficulty difficulty, Random random, Scheduler scheduler,
            long thinkMillis) {
        this.difficulty = difficulty;
        this.random = random;
        this.scheduler = scheduler;
        this.thinkMillis = thinkMillis;
    }

    private static Scheduler defaultScheduler() {
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "computer-player");
            t.setDaemon(true);
            return t;
        });
        return (task, delay) -> exec.schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    public String nickname() {
        return "Computer (" + difficulty.label() + ")";
    }

    @Override
    public void startServer(int port, String nickname, NetworkListener l) {
        throw new UnsupportedOperationException("The computer joins, it does not host");
    }

    /** "Connects" at once: the computer places its fleet, is ready, and greets the listener. */
    @Override
    public void connect(String host, int port, String nickname, NetworkListener l) {
        this.listener = l;
        closed = false;
        synchronized (this) {
            newRound();
        }
        l.onConnected(nickname(), "computer");
        String fleetText = ProtocolCodec.encodeFleet(fleet.fleet());
        scheduler.schedule(() -> deliver(Message.ready(fleetText)), thinkMillis / 2);
    }

    private void newRound() {
        engine = new GameEngine(false);
        ai = new ComputerPlayer(difficulty, random);
        fleet = RandomFleetGenerator.generate(random);
        engine.setLocalFleet(fleet.copyPlacement());
        engine.markReady(true);
    }

    @Override
    public boolean send(Message m) {
        if (closed) {
            return false;
        }
        scheduler.schedule(() -> handle(m), 0);
        return true;
    }

    private synchronized void handle(Message m) {
        if (closed) {
            return;
        }
        try {
            switch (m.type()) {
                case READY -> onReady();
                case SHOOT -> onShoot(Coordinate.parse(m.field(0)));
                case RADAR -> onRadar(Coordinate.parse(m.field(0)));
                case TORPEDO -> onTorpedo(Coordinate.parse(m.field(0)));
                case RESULT -> onResult(m);
                case RADAR_RESULT -> engine.applyRadarResult(Coordinate.parse(m.field(0)),
                        new HashSet<>(ProtocolCodec.decodeCells(m.field(1))));
                case TORPEDO_RESULT -> onTorpedoResult(m);
                case ERROR -> {
                    engine.cancelPendingShot();
                    scheduleMove();
                }
                case CHAT -> scheduler.schedule(
                        () -> deliver(Message.chat("CLIENT", CHAT_REPLIES[random.nextInt(CHAT_REPLIES.length)])),
                        thinkMillis);
                default -> {
                    // START, QUIT, GAME_OVER, PING: nothing to do
                }
            }
        } catch (GameRuleException e) {
            deliver(Message.error(e.violation().name(), e.getMessage()));
        } catch (ProtocolException | IllegalArgumentException e) {
            LOG.warning("Computer ignored a bad message: " + e.getMessage());
        }
    }

    private void onReady() {
        if (engine.state() == GameState.FINISHED || engine.state() == GameState.ABORTED) {
            newRound();
            deliver(Message.ready(ProtocolCodec.encodeFleet(fleet.fleet())));
        }
        if (engine.state() == GameState.SETUP || engine.state() == GameState.WAITING) {
            engine.markReady(false);
        }
    }

    private void onShoot(Coordinate c) {
        ShotOutcome o = engine.receiveEnemyShot(c);
        if (o.result() == ShotResult.ALREADY_SHOT) {
            deliver(Message.error("ALREADY_SHOT", "Already shot at " + c));
            return;
        }
        deliver(Message.result(c.toString(), o.result().name(), o.sunkType() == null ? null : o.sunkType().name()));
        afterEnemyAction();
    }

    private void onRadar(Coordinate c) {
        List<Coordinate> cells = engine.receiveEnemyRadar(c);
        deliver(Message.radarResult(c.toString(), ProtocolCodec.encodeCells(cells)));
        afterEnemyAction();
    }

    private void onTorpedo(Coordinate c) {
        List<ShotOutcome> outcomes = engine.receiveEnemyTorpedo(c);
        deliver(Message.torpedoResult(c.toString(), ProtocolCodec.encodeOutcomes(outcomes)));
        afterEnemyAction();
    }

    private void afterEnemyAction() {
        if (engine.state() == GameState.FINISHED) {
            deliver(Message.gameOver(true));
        } else {
            scheduleMove();
        }
    }

    private void onResult(Message m) {
        Coordinate c = Coordinate.parse(m.field(0));
        ShotResult r = ShotResult.valueOf(m.field(1));
        ShipType sunk = m.field(2).isEmpty() ? null : ShipType.valueOf(m.field(2));
        engine.applyOpponentShotResult(c, r, sunk);
        if (r == ShotResult.SUNK && sunk != null) {
            ai.onSunk(c, sunk, engine.myShotResults());
        }
        scheduleMove();
    }

    private void onTorpedoResult(Message m) throws ProtocolException {
        Coordinate c = Coordinate.parse(m.field(0));
        List<ShotOutcome> outcomes = ProtocolCodec.decodeOutcomes(m.field(1));
        engine.applyTorpedoResult(c, outcomes);
        for (ShotOutcome o : outcomes) {
            if (o.result() == ShotResult.SUNK && o.sunkType() != null) {
                ai.onSunk(o.coordinate(), o.sunkType(), engine.myShotResults());
            }
        }
        scheduleMove();
    }

    private void scheduleMove() {
        scheduler.schedule(this::makeMove, thinkMillis);
    }

    private synchronized void makeMove() {
        if (closed || !engine.isLocalTurn()) {
            return;
        }
        ComputerPlayer.Move move = ai.nextMove(engine);
        Weapon weapon = move.weapon();
        Coordinate target = move.target();
        try {
            engine.use(weapon, target);
        } catch (GameRuleException e) {
            weapon = Weapon.SHOT;
            target = firstOpenCell();
            engine.use(weapon, target);
        }
        deliver(switch (weapon) {
            case SHOT -> Message.shoot(target.toString());
            case RADAR -> Message.radar(target.toString());
            case TORPEDO -> Message.torpedo(target.toString());
        });
    }

    private Coordinate firstOpenCell() {
        for (int r = 0; r < Coordinate.SIZE; r++) {
            for (int c = 1; c <= Coordinate.SIZE; c++) {
                Coordinate cell = Coordinate.of(r, c);
                if (!engine.hasShotAt(cell)) {
                    return cell;
                }
            }
        }
        throw new IllegalStateException("No open cell left");
    }

    private void deliver(Message m) {
        NetworkListener l = listener;
        if (!closed && l != null) {
            l.onMessage(m);
        }
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public void dropClient() {
        close();
    }

    @Override
    public boolean isListening() {
        return false;
    }

    @Override
    public String localIp() {
        return "127.0.0.1";
    }

    /** The computer's fleet (for tests). */
    synchronized Board fleet() {
        return fleet;
    }
}
