package com.amiral.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Radar and torpedo rules of {@link GameEngine}. Fleet: one ship per row A-E from column 1. */
class GameEngineWeaponsTest {

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
    void enemyRadarFindsShipCellsWithoutHittingThem() {
        GameEngine e = playing(false);
        List<Coordinate> found = e.receiveEnemyRadar(c("B2"));
        // Area A1-D4: carrier A1-A4, battleship B1-B4, cruiser C1-C3, submarine D1-D3.
        assertEquals(14, found.size());
        assertTrue(found.contains(c("A1")));
        assertFalse(found.contains(c("C4")));
        assertTrue(e.isLocalTurn(), "radar ends the enemy's turn");
        assertTrue(e.localBoard().shots().isEmpty(), "radar does not shoot");
        assertEquals(2, e.enemyUsesLeft(Weapon.RADAR));
    }

    @Test
    void enemyTorpedoShootsWholeArea() {
        GameEngine e = playing(false);
        List<ShotOutcome> out = e.receiveEnemyTorpedo(c("E2"));
        // Area D1-F3: submarine D1-D3 sunk, destroyer E1-E2 sunk.
        assertEquals(9, out.size());
        assertEquals(2, out.stream().filter(o -> o.result() == ShotResult.SUNK).count());
        assertEquals(5, out.stream().filter(o -> o.result().isHit()).count());
        assertTrue(e.isLocalTurn());
        assertEquals(1, e.enemyUsesLeft(Weapon.TORPEDO));
    }

    @Test
    void enemyTorpedoSkipsShotCellsAndNeedsANewOne() {
        GameEngine e = playing(false);
        e.receiveEnemyShot(c("J10"));
        e.fireAt(c("A1"));
        e.applyOpponentShotResult(c("A1"), ShotResult.MISS, null);
        assertEquals(8, e.receiveEnemyTorpedo(c("I9")).size());
        e.fireAt(c("A2"));
        e.applyOpponentShotResult(c("A2"), ShotResult.MISS, null);
        assertEquals(RuleViolation.ALREADY_SHOT, violation(() -> e.receiveEnemyTorpedo(c("J10"))));
        assertFalse(e.isLocalTurn(), "turn unchanged after refusal");
    }

    @Test
    void enemyLimitsAreEnforced() {
        GameEngine e = playing(false);
        for (int i = 0; i < 3; i++) {
            e.receiveEnemyRadar(c("H8"));
            Coordinate mine = Coordinate.of(9, i + 1);
            e.fireAt(mine);
            e.applyOpponentShotResult(mine, ShotResult.MISS, null);
        }
        assertEquals(RuleViolation.NO_ABILITY_LEFT, violation(() -> e.receiveEnemyRadar(c("H8"))));
    }

    @Test
    void enemyTorpedoCanSinkTheLastShip() {
        GameEngine e = playing(false);
        String[] cells = Fleets.standardCells();
        for (int i = 0; i < cells.length - 2; i++) {
            e.receiveEnemyShot(c(cells[i]));
            Coordinate mine = Coordinate.of(5 + i / 10, i % 10 + 1);
            e.fireAt(mine);
            e.applyOpponentShotResult(mine, ShotResult.MISS, null);
        }
        e.receiveEnemyTorpedo(c("E2"));
        assertEquals(GameState.FINISHED, e.state());
        assertFalse(e.winner().orElseThrow());
    }

    @Test
    void ourRadarStoresFindings() {
        GameEngine e = playing(true);
        e.useRadar(c("E5"));
        assertFalse(e.isLocalTurn());
        assertEquals(Weapon.RADAR, e.pendingWeapon());
        assertEquals(2, e.usesLeft(Weapon.RADAR));
        e.applyRadarResult(c("E5"), Set.of(c("E5"), c("E6")));
        assertEquals(Set.of(c("E5"), c("E6")), e.detectedShipCells());
        assertEquals(16, e.scannedCells().size());
        assertTrue(e.isKnownWater(c("D4")));
        assertFalse(e.isKnownWater(c("E5")));
        assertFalse(e.hasShotAt(c("E5")), "radar is not a shot");
    }

    @Test
    void radarResultOutsideAreaIsRejected() {
        GameEngine e = playing(true);
        e.useRadar(c("E5"));
        assertEquals(RuleViolation.INVALID_COORD, violation(() -> e.applyRadarResult(c("E5"), Set.of(c("A1")))));
        assertEquals(RuleViolation.INVALID_COORD, violation(() -> e.applyRadarResult(c("A5"), Set.of())));
    }

    @Test
    void ourTorpedoCountsHitsAndCanWin() {
        GameEngine e = playing(true);
        e.fireTorpedo(c("B2"));
        e.applyTorpedoResult(c("B2"), List.of(
                ShotOutcome.of(c("A1"), ShotResult.HIT),
                ShotOutcome.of(c("B1"), ShotResult.MISS),
                new ShotOutcome(c("C3"), ShotResult.SUNK, ShipType.DESTROYER, List.of())));
        assertEquals(2, e.enemyHits());
        assertTrue(e.enemySunk().contains(ShipType.DESTROYER));
        assertEquals(1, e.usesLeft(Weapon.TORPEDO));
        assertEquals(RuleViolation.INVALID_COORD, violation(() -> e.applyTorpedoResult(c("B2"), List.of())));
    }

    @Test
    void torpedoResultMustBeInsideArea() {
        GameEngine e = playing(true);
        e.fireTorpedo(c("B2"));
        assertEquals(RuleViolation.INVALID_COORD, violation(() -> e.applyTorpedoResult(c("B2"),
                List.of(ShotOutcome.of(c("J10"), ShotResult.MISS)))));
    }

    @Test
    void ourLimitsAndTurnAreEnforced() {
        GameEngine e = playing(true);
        for (int i = 0; i < 3; i++) {
            e.useRadar(c("E5"));
            e.applyRadarResult(c("E5"), Set.of());
            e.receiveEnemyShot(Coordinate.of(9, i + 1));
        }
        assertEquals(0, e.usesLeft(Weapon.RADAR));
        assertEquals(RuleViolation.NO_ABILITY_LEFT, violation(() -> e.useRadar(c("A1"))));
        assertTrue(e.isLocalTurn(), "refused action keeps the turn");
        e.fireTorpedo(c("A1"));
        assertEquals(RuleViolation.NOT_YOUR_TURN, violation(() -> e.fireTorpedo(c("A1"))));
    }

    @Test
    void torpedoNeedsAnUnshotCell() {
        GameEngine e = playing(true);
        e.fireTorpedo(c("B2"));
        List<ShotOutcome> all = Weapon.TORPEDO.area(c("B2")).stream()
                .map(cell -> ShotOutcome.of(cell, ShotResult.MISS)).toList();
        e.applyTorpedoResult(c("B2"), all);
        e.receiveEnemyShot(c("J10"));
        assertEquals(RuleViolation.ALREADY_SHOT, violation(() -> e.fireTorpedo(c("B2"))));
        assertEquals(1, e.usesLeft(Weapon.TORPEDO), "refused torpedo is not used up");
    }

    @Test
    void cancelGivesWeaponBack() {
        GameEngine e = playing(true);
        e.fireTorpedo(c("E5"));
        e.cancelPendingShot();
        assertEquals(2, e.usesLeft(Weapon.TORPEDO));
        assertTrue(e.isLocalTurn());
    }
}
