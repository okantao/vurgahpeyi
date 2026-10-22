package com.amiral.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ComputerPlayerTest {

    /** Result of one simulated game: turns needed and weapons used. */
    record Run(int turns, int radars, int torpedoes) {
    }

    /**
     * The computer plays against a fixed target board. Every move must be legal; the opponent only
     * shoots at water so the computer always gets to finish.
     */
    static Run play(ComputerPlayer.Difficulty d, long seed) {
        Random rnd = new Random(seed);
        Board target = RandomFleetGenerator.generate(rnd);
        Board own = RandomFleetGenerator.generate(rnd);
        GameEngine engine = new GameEngine(true);
        engine.setLocalFleet(own);
        engine.markReady(true);
        engine.markReady(false);
        ComputerPlayer ai = new ComputerPlayer(d, rnd);
        List<Coordinate> water = new ArrayList<>();
        for (int r = 0; r < 10; r++) {
            for (int c = 1; c <= 10; c++) {
                if (own.shipAt(Coordinate.of(r, c)).isEmpty()) {
                    water.add(Coordinate.of(r, c));
                }
            }
        }
        int turns = 0;
        int radars = 0;
        int torpedoes = 0;
        while (engine.state() == GameState.PLAYING) {
            ComputerPlayer.Move m = ai.nextMove(engine);
            if (m.weapon() == Weapon.SHOT) {
                assertFalse(engine.hasShotAt(m.target()), "never shoots the same cell twice");
                assertFalse(engine.isKnownWater(m.target()), "never shoots scanned water");
            }
            engine.use(m.weapon(), m.target());
            turns++;
            switch (m.weapon()) {
                case SHOT -> {
                    ShotOutcome o = target.receiveShot(m.target());
                    engine.applyOpponentShotResult(m.target(), o.result(), o.sunkType());
                    if (o.result() == ShotResult.SUNK) {
                        ai.onSunk(m.target(), o.sunkType(), engine.myShotResults());
                    }
                }
                case RADAR -> {
                    radars++;
                    Set<Coordinate> found = new HashSet<>();
                    for (Coordinate cell : Weapon.RADAR.area(m.target())) {
                        if (target.shipAt(cell).isPresent()) {
                            found.add(cell);
                        }
                    }
                    engine.applyRadarResult(m.target(), found);
                }
                case TORPEDO -> {
                    torpedoes++;
                    List<ShotOutcome> outs = new ArrayList<>();
                    for (Coordinate cell : Weapon.TORPEDO.area(m.target())) {
                        if (!target.wasShot(cell)) {
                            outs.add(target.receiveShot(cell));
                        }
                    }
                    engine.applyTorpedoResult(m.target(), outs);
                    for (ShotOutcome o : outs) {
                        if (o.result() == ShotResult.SUNK) {
                            ai.onSunk(o.coordinate(), o.sunkType(), engine.myShotResults());
                        }
                    }
                }
            }
            if (engine.state() == GameState.PLAYING && !engine.isLocalTurn()) {
                engine.receiveEnemyShot(water.remove(0));
            }
            assertTrue(turns <= 100, "game must end");
        }
        assertTrue(target.allSunk());
        assertTrue(engine.winner().orElseThrow());
        return new Run(turns, radars, torpedoes);
    }

    @ParameterizedTest
    @EnumSource(ComputerPlayer.Difficulty.class)
    void everyLevelFinishesWithLegalMoves(ComputerPlayer.Difficulty d) {
        for (long seed = 1; seed <= 15; seed++) {
            Run run = play(d, seed);
            assertTrue(run.radars() <= 3 && run.torpedoes() <= 2);
        }
    }

    @Test
    void harderLevelsNeedFewerTurns() {
        double easy = 0;
        double normal = 0;
        double hard = 0;
        int games = 30;
        for (long seed = 100; seed < 100 + games; seed++) {
            easy += play(ComputerPlayer.Difficulty.EASY, seed).turns();
            normal += play(ComputerPlayer.Difficulty.NORMAL, seed).turns();
            hard += play(ComputerPlayer.Difficulty.HARD, seed).turns();
        }
        assertTrue(normal < easy, "normal " + normal / games + " vs easy " + easy / games);
        assertTrue(hard < normal, "hard " + hard / games + " vs normal " + normal / games);
    }

    @Test
    void hardUsesItsSpecialWeapons() {
        int radars = 0;
        int torpedoes = 0;
        for (long seed = 1; seed <= 10; seed++) {
            Run run = play(ComputerPlayer.Difficulty.HARD, seed);
            radars += run.radars();
            torpedoes += run.torpedoes();
        }
        assertTrue(radars > 0);
        assertTrue(torpedoes > 0);
    }

    @Test
    void shootsRadarFindsFirst() {
        GameEngine engine = new GameEngine(true);
        engine.setLocalFleet(Fleets.standard());
        engine.markReady(true);
        engine.markReady(false);
        engine.useRadar(Coordinate.parse("E5"));
        engine.applyRadarResult(Coordinate.parse("E5"), Set.of(Coordinate.parse("F6")));
        engine.receiveEnemyShot(Coordinate.parse("J10"));
        ComputerPlayer ai = new ComputerPlayer(ComputerPlayer.Difficulty.NORMAL, new Random(1));
        assertEquals(new ComputerPlayer.Move(Weapon.SHOT, Coordinate.parse("F6")), ai.nextMove(engine));
    }

    @Test
    void followsAHit() {
        GameEngine engine = new GameEngine(true);
        engine.setLocalFleet(Fleets.standard());
        engine.markReady(true);
        engine.markReady(false);
        engine.fireAt(Coordinate.parse("E5"));
        engine.applyOpponentShotResult(Coordinate.parse("E5"), ShotResult.HIT, null);
        ComputerPlayer ai = new ComputerPlayer(ComputerPlayer.Difficulty.NORMAL, new Random(1));
        ComputerPlayer.Move m = ai.nextMove(engine);
        assertEquals(Weapon.SHOT, m.weapon());
        assertTrue(List.of("D5", "F5", "E4", "E6").contains(m.target().toString()), m.toString());
    }
}
