package com.amiral.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CoordinateTest {

    @Test
    void parsesValidCoordinatesCaseInsensitive() {
        assertEquals(new Coordinate('A', 1), Coordinate.parse("A1"));
        assertEquals(new Coordinate('J', 10), Coordinate.parse("j10"));
        assertEquals(new Coordinate('E', 5), Coordinate.parse(" e5 "));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"K1", "A11", "A0", "1A", "AA", "A", "A1 0", "Z99", "A-1"})
    void rejectsInvalidText(String text) {
        assertThrows(IllegalArgumentException.class, () -> Coordinate.parse(text));
    }

    @Test
    void constructorValidatesRange() {
        assertThrows(IllegalArgumentException.class, () -> new Coordinate('K', 1));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate('@', 1));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate('A', 0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate('A', 11));
    }

    @Test
    void toStringRoundTrips() {
        assertEquals("A1", Coordinate.parse("a1").toString());
        assertEquals("J10", Coordinate.parse("J10").toString());
    }

    @Test
    void ofUsesZeroBasedRow() {
        assertEquals(Coordinate.parse("A1"), Coordinate.of(0, 1));
        assertEquals(Coordinate.parse("J10"), Coordinate.of(9, 10));
        assertEquals(9, Coordinate.parse("J3").rowIndex());
        assertThrows(IllegalArgumentException.class, () -> Coordinate.of(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> Coordinate.of(10, 1));
    }

    @Test
    void offsetStaysInsideBoard() {
        Coordinate a1 = Coordinate.parse("A1");
        assertEquals(Coordinate.parse("B2"), a1.offset(1, 1).orElseThrow());
        assertTrue(a1.offset(-1, 0).isEmpty());
        assertTrue(a1.offset(0, -1).isEmpty());
        Coordinate j10 = Coordinate.parse("J10");
        assertTrue(j10.offset(1, 0).isEmpty());
        assertTrue(j10.offset(0, 1).isEmpty());
        assertEquals(Coordinate.parse("I9"), j10.offset(-1, -1).orElseThrow());
    }
}
