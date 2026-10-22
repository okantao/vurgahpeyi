package com.amiral.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameEngineTest {

    private static Coordinate c(String s) {
        return Coordinate.parse(s);
    }

    private static GameEngine playing(boolean server) {
        GameEngine e = new GameEngine(server);
        e.setLocalFleet(Fleets.standard());
        e.markReady(true);
        e.markReady(false);
        return e;
    }

    private static RuleViolation violation(Runnable r) {
        return assertThrows(GameRuleException.class, r::run).violation();
    }

    @Test
    void setupRequiresCompleteFleet() {
        GameEngine e = new GameEngine(true);
        assertEquals(RuleViolation.INVALID_FLEET, violation(() -> e.setLocalFleet(new Board())));
        assertEquals(RuleViolation.INVALID_FLEET, violation(() -> e.markReady(true)));
        assertThrows(NullPointerException.class, () -> e.setLocalFleet(null));
    }

    @Test
    void readyTransitions() {
        GameEngine e = new GameEngine(true);
        e.markReady(false);
        assertEquals(GameState.SETUP, e.state());
        assertTrue(e.isRemoteReady());
        e.setLocalFleet(Fleets.standard());
        e.markReady(true);
        assertEquals(GameState.PLAYING, e.state());
        assertTrue(e.isLocalReady());
        assertTrue(e.isLocalTurn(), "server starts");
    }

    @Test
    void waitingUntilOpponentReady() {
        GameEngine e = new GameEngine(false);
        e.setLocalFleet(Fleets.standard());
        e.markReady(true);
        assertEquals(GameState.WAITING, e.state());
        e.markReady(false);
        assertEquals(GameState.PLAYING, e.state());
        assertFalse(e.isLocalTurn(), "client waits for server");
    }

    @Test
    void invalidTransitionsThrow() {
        GameEngine e = playing(true);
        assertEquals(RuleViolation.INVALID_STATE, violation(() -> e.setLocalFleet(Fleets.standard())));
        assertEquals(RuleViolation.INVALID_STATE, violation(() -> e.markReady(true)));
        GameEngine fresh = new GameEngine(true);
        assertEquals(RuleViolation.GAME_NOT_STARTED, violation(() -> fresh.fireAt(c("A1"))));
        assertEquals(RuleViolation.GAME_NOT_STARTED, violation(() -> fresh.receiveEnemyShot(c("A1"))));
    }

    @Test
    void hitKeepsTheTurnAndMissPassesIt() {
        GameEngine e = playing(true);
        e.fireAt(c("A1"));
        assertFalse(e.isLocalTurn());
        e.applyOpponentShotResult(c("A1"), ShotResult.HIT, null);
        assertTrue(e.isLocalTurn(), "a hit keeps the turn");
        e.fireAt(c("A2"));
        e.applyOpponentShotResult(c("A2"), ShotResult.MISS, null);
        assertFalse(e.isLocalTurn(), "a miss passes the turn");
        assertEquals(ShotResult.HIT, e.receiveEnemyShot(c("A1")).result());
        assertFalse(e.isLocalTurn(), "the opponent keeps the turn after a hit");
        assertEquals(ShotResult.MISS, e.receiveEnemyShot(c("J10")).result());
        assertTrue(e.isLocalTurn());
        assertEquals(1, e.enemyHits());
        assertEquals(ShotResult.HIT, e.myShotResults().get(c("A1")));
    }

    @Test
    void wrongTurnIsRejected() {
        GameEngine client = playing(false);
        assertEquals(RuleViolation.NOT_YOUR_TURN, violation(() -> client.fireAt(c("A1"))));
        GameEngine server = playing(true);
        assertEquals(RuleViolation.NOT_YOUR_TURN, violation(() -> server.receiveEnemyShot(c("A1"))));
        server.fireAt(c("B5"));
        assertEquals(RuleViolation.NOT_YOUR_TURN, violation(() -> server.receiveEnemyShot(c("A1"))),
                "opponent cannot shoot before reporting our result");
    }

    @Test
    void repeatedShotsAreRejected() {
        GameEngine e = playing(true);
        e.fireAt(c("A1"));
        e.applyOpponentShotResult(c("A1"), ShotResult.MISS, null);
        e.receiveEnemyShot(c("J10"));
        assertEquals(RuleViolation.ALREADY_SHOT, violation(() -> e.fireAt(c("A1"))));
        assertTrue(e.isLocalTurn(), "turn unchanged");
        e.fireAt(c("A2"));
        e.applyOpponentShotResult(c("A2"), ShotResult.MISS, null);
        assertEquals(ShotResult.ALREADY_SHOT, e.receiveEnemyShot(c("J10")).result());
        assertFalse(e.isLocalTurn());
    }

    @Test
    void resultMustMatchPendingShot() {
        GameEngine e = playing(true);
        e.fireAt(c("A1"));
        assertEquals(RuleViolation.INVALID_COORD,
                violation(() -> e.applyOpponentShotResult(c("A2"), ShotResult.MISS, null)));
        assertEquals(RuleViolation.ALREADY_SHOT,
                violation(() -> e.applyOpponentShotResult(c("A1"), ShotResult.ALREADY_SHOT, null)));
    }

    @Test
    void cancelPendingShotGivesTurnBack() {
        GameEngine e = playing(true);
        e.fireAt(c("A1"));
        e.cancelPendingShot();
        assertTrue(e.isLocalTurn());
        assertFalse(e.hasShotAt(c("A1")));
    }

    @Test
    void localWinsAfterSeventeenHits() {
        GameEngine e = playing(true);
        String[] targets = Fleets.standardCells();
        for (int i = 0; i < targets.length; i++) {
            assertTrue(e.winner().isEmpty());
            e.fireAt(c(targets[i]));
            ShotResult r = i == targets.length - 1 ? ShotResult.SUNK : ShotResult.HIT;
            e.applyOpponentShotResult(c(targets[i]), r, r == ShotResult.SUNK ? ShipType.DESTROYER : null);
        }
        assertEquals(GameState.FINISHED, e.state());
        assertTrue(e.winner().orElseThrow());
        assertTrue(e.enemySunk().contains(ShipType.DESTROYER));
        assertFalse(e.isLocalTurn());
    }

    /** Client engine whose fleet is sunk by the opponent in one run of hits. */
    private static GameEngine lostGame() {
        GameEngine e = playing(false);
        String[] cells = Fleets.standardCells();
        for (int i = 0; i < cells.length; i++) {
            ShotOutcome o = e.receiveEnemyShot(c(cells[i]));
            if (i == cells.length - 1) {
                assertEquals(ShotResult.SUNK, o.result());
                assertEquals(ShipType.DESTROYER, o.sunkType());
            } else {
                assertFalse(e.isLocalTurn(), "the opponent keeps shooting after a hit");
            }
        }
        return e;
    }

    @Test
    void opponentWinsWhenOurFleetSinks() {
        GameEngine e = lostGame();
        assertEquals(GameState.FINISHED, e.state());
        assertFalse(e.winner().orElseThrow());
    }

    @Test
    void abortedGameRejectsShots() {
        GameEngine e = playing(true);
        e.abort();
        assertEquals(GameState.ABORTED, e.state());
        assertEquals(RuleViolation.GAME_NOT_STARTED, violation(() -> e.fireAt(c("A1"))));
        assertTrue(e.winner().isEmpty());
        assertFalse(e.isLocalTurn());
    }

    @Test
    void finishedGameIsNotAborted() {
        GameEngine e = lostGame();
        e.abort();
        assertEquals(GameState.FINISHED, e.state());
        assertFalse(e.isLocalServer());
        assertEquals(Fleets.standard().fleet(), e.localBoard().fleet());
    }
}
