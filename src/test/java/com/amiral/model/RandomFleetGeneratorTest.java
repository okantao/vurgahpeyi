package com.amiral.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RandomFleetGeneratorTest {

    @Test
    void thousandFleetsAreValid() {
        Random rnd = new Random(42);
        for (int i = 0; i < 1000; i++) {
            Board b = RandomFleetGenerator.generate(rnd);
            assertTrue(b.isFleetComplete());
            Set<Coordinate> cells = new HashSet<>();
            for (Ship s : b.ships()) {
                assertTrue(s.fitsOnBoard());
                cells.addAll(s.cells());
            }
            assertEquals(ShipType.FLEET_CELLS, cells.size(), "no overlap");
        }
    }

    @Test
    void sameSeedSameFleet() {
        assertEquals(RandomFleetGenerator.generate(new Random(7)).fleet(),
                RandomFleetGenerator.generate(new Random(7)).fleet());
    }

    @Test
    void failsWhenNoSpaceLeft() {
        Board full = new Board();
        // Five ships cannot block a carrier, so use a fake board that rejects everything.
        Board blocked = new Board() {
            @Override
            public boolean canPlace(Ship s) {
                return false;
            }
        };
        assertThrows(IllegalStateException.class,
                () -> RandomFleetGenerator.randomShip(blocked, ShipType.CARRIER, new Random(1)));
        assertTrue(RandomFleetGenerator.randomShip(full, ShipType.CARRIER, new Random(1)).fitsOnBoard());
    }
}
