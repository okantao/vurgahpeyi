package com.amiral.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BoardTest {

    private static Coordinate c(String s) {
        return Coordinate.parse(s);
    }

    @Test
    void placesHorizontalAndVertical() {
        Board b = new Board();
        b.placeShip(new Ship(ShipType.CARRIER, c("A1"), Orientation.H));
        b.placeShip(new Ship(ShipType.BATTLESHIP, c("C3"), Orientation.V));
        assertEquals(ShipType.CARRIER, b.shipAt(c("A5")).orElseThrow().type());
        assertEquals(ShipType.BATTLESHIP, b.shipAt(c("F3")).orElseThrow().type());
        assertTrue(b.shipAt(c("G3")).isEmpty());
        assertEquals(2, b.ships().size());
        assertFalse(b.isFleetComplete());
    }

    @Test
    void rejectsOverlapOutOfBoundsAndDuplicateType() {
        Board b = new Board();
        b.placeShip(new Ship(ShipType.CARRIER, c("A1"), Orientation.H));
        assertFalse(b.canPlace(new Ship(ShipType.BATTLESHIP, c("A3"), Orientation.V)));
        assertThrows(IllegalArgumentException.class,
                () -> b.placeShip(new Ship(ShipType.BATTLESHIP, c("A3"), Orientation.V)));
        assertFalse(b.canPlace(new Ship(ShipType.BATTLESHIP, c("J8"), Orientation.H)));
        assertFalse(b.canPlace(new Ship(ShipType.CARRIER, c("F1"), Orientation.H)));
        assertFalse(b.canPlace(null));
        assertTrue(b.canPlace(new Ship(ShipType.BATTLESHIP, c("B1"), Orientation.H)));
    }

    @Test
    void adjacentShipsAllowed() {
        Board b = new Board();
        b.placeShip(new Ship(ShipType.CARRIER, c("A1"), Orientation.H));
        assertTrue(b.canPlace(new Ship(ShipType.DESTROYER, c("B1"), Orientation.H)));
    }

    @Test
    void fullFleetIsComplete() {
        Board b = Fleets.standard();
        assertTrue(b.isFleetComplete());
        assertEquals(5, b.fleet().size());
        assertTrue(Board.of(b.fleet()).isFleetComplete());
        assertTrue(b.copyPlacement().isFleetComplete());
    }

    @Test
    void missHitSunkSequence() {
        Board b = Fleets.standard();
        assertEquals(ShotResult.MISS, b.receiveShot(c("J10")).result());
        assertEquals(ShotResult.HIT, b.receiveShot(c("E1")).result());
        assertTrue(b.sunkShipAt(c("E1")).isEmpty());
        ShotOutcome sunk = b.receiveShot(c("E2"));
        assertEquals(ShotResult.SUNK, sunk.result());
        assertEquals(ShipType.DESTROYER, sunk.sunkType());
        assertEquals(List.of(c("E1"), c("E2")), sunk.sunkCells());
        assertEquals(ShipType.DESTROYER, b.sunkShipAt(c("E1")).orElseThrow());
        assertTrue(b.sunkShipAt(c("J10")).isEmpty());
        assertTrue(b.wasShot(c("J10")));
    }

    @Test
    void secondShotOnSameCell() {
        Board b = Fleets.standard();
        b.receiveShot(c("A1"));
        ShotOutcome again = b.receiveShot(c("A1"));
        assertEquals(ShotResult.ALREADY_SHOT, again.result());
        assertNull(again.sunkType());
        assertEquals(1, b.shots().size());
    }

    @Test
    void allSunkAfterEveryCellHit() {
        Board b = Fleets.standard();
        assertFalse(new Board().allSunk());
        String[] cells = Fleets.standardCells();
        for (int i = 0; i < cells.length; i++) {
            assertFalse(b.allSunk());
            b.receiveShot(c(cells[i]));
        }
        assertTrue(b.allSunk());
    }

    @Test
    void removeAndClear() {
        Board b = Fleets.standard();
        assertTrue(b.removeShip(ShipType.CARRIER));
        assertFalse(b.removeShip(ShipType.CARRIER));
        assertTrue(b.shipAt(c("A1")).isEmpty());
        assertTrue(b.canPlace(new Ship(ShipType.CARRIER, c("A1"), Orientation.H)));
        b.receiveShot(c("B1"));
        assertFalse(b.removeShip(ShipType.BATTLESHIP));
        b.clear();
        assertTrue(b.ships().isEmpty());
        assertTrue(b.shots().isEmpty());
    }
}
