package com.amiral.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeaponTest {

    private static Coordinate c(String s) {
        return Coordinate.parse(s);
    }

    @Test
    void usesPerGame() {
        assertEquals(3, Weapon.RADAR.uses());
        assertEquals(2, Weapon.TORPEDO.uses());
        assertEquals(Integer.MAX_VALUE, Weapon.SHOT.uses());
    }

    @Test
    void shotCoversOneCell() {
        assertEquals(List.of(c("E5")), Weapon.SHOT.area(c("E5")));
    }

    @Test
    void torpedoIsCentred3x3() {
        List<Coordinate> area = Weapon.TORPEDO.area(c("E5"));
        assertEquals(9, area.size());
        assertEquals(c("D4"), area.get(0));
        assertEquals(c("F6"), area.get(8));
    }

    @Test
    void radarIs4x4AroundAnchor() {
        List<Coordinate> area = Weapon.RADAR.area(c("E5"));
        assertEquals(16, area.size());
        assertEquals(c("D4"), area.get(0));
        assertEquals(c("G7"), area.get(15));
    }

    @Test
    void areasShiftInsideTheBoard() {
        assertEquals(c("A1"), Weapon.TORPEDO.area(c("A1")).get(0));
        assertEquals(c("J10"), Weapon.TORPEDO.area(c("J10")).get(8));
        assertEquals(c("G7"), Weapon.RADAR.area(c("J10")).get(0));
        for (int r = 0; r < 10; r++) {
            for (int col = 1; col <= 10; col++) {
                assertEquals(16, new HashSet<>(Weapon.RADAR.area(Coordinate.of(r, col))).size());
                assertEquals(9, new HashSet<>(Weapon.TORPEDO.area(Coordinate.of(r, col))).size());
            }
        }
        assertTrue(Weapon.RADAR.area(c("A1")).contains(c("D4")));
    }
}
