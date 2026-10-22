package com.amiral.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShipTest {

    private static Coordinate c(String s) {
        return Coordinate.parse(s);
    }

    @Test
    void horizontalCells() {
        Ship ship = new Ship(ShipType.CRUISER, c("B2"), Orientation.H);
        assertEquals(List.of(c("B2"), c("B3"), c("B4")), ship.cells());
    }

    @Test
    void verticalCells() {
        Ship ship = new Ship(ShipType.DESTROYER, c("I10"), Orientation.V);
        assertEquals(List.of(c("I10"), c("J10")), ship.cells());
    }

    @Test
    void overflowThrows() {
        Ship ship = new Ship(ShipType.DESTROYER, c("J10"), Orientation.H);
        assertFalse(ship.fitsOnBoard());
        assertThrows(IllegalStateException.class, ship::cells);
        assertFalse(ship.occupies(c("J10")));
        assertThrows(IllegalStateException.class,
                () -> new Ship(ShipType.CARRIER, c("G1"), Orientation.V).cells());
    }

    @Test
    void occupies() {
        Ship ship = new Ship(ShipType.CARRIER, c("A1"), Orientation.V);
        assertTrue(ship.occupies(c("E1")));
        assertFalse(ship.occupies(c("F1")));
    }

    @Test
    void isSunkNeedsAllCells() {
        Ship ship = new Ship(ShipType.DESTROYER, c("A1"), Orientation.H);
        assertFalse(ship.isSunk(Set.of(c("A1"))));
        assertTrue(ship.isSunk(Set.of(c("A1"), c("A2"), c("C5"))));
    }

    @Test
    void shipTypesMakeSeventeenCells() {
        int total = 0;
        for (ShipType t : ShipType.values()) {
            total += t.length();
        }
        assertEquals(ShipType.FLEET_CELLS, total);
        assertEquals("Carrier (5)", ShipType.CARRIER.label());
        assertEquals(Orientation.V, Orientation.H.toggle());
        assertEquals(Orientation.H, Orientation.V.toggle());
    }

    @Test
    void nullFieldsRejected() {
        assertThrows(NullPointerException.class, () -> new Ship(null, c("A1"), Orientation.H));
    }
}
