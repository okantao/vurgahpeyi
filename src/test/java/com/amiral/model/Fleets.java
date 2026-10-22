package com.amiral.model;

/** Test fixtures: a fixed, well-known fleet. */
public final class Fleets {

    private Fleets() {
    }

    /** One ship per row A-E, all starting at column 1 and horizontal. */
    public static Board standard() {
        Board b = new Board();
        b.placeShip(new Ship(ShipType.CARRIER, Coordinate.parse("A1"), Orientation.H));
        b.placeShip(new Ship(ShipType.BATTLESHIP, Coordinate.parse("B1"), Orientation.H));
        b.placeShip(new Ship(ShipType.CRUISER, Coordinate.parse("C1"), Orientation.H));
        b.placeShip(new Ship(ShipType.SUBMARINE, Coordinate.parse("D1"), Orientation.H));
        b.placeShip(new Ship(ShipType.DESTROYER, Coordinate.parse("E1"), Orientation.H));
        return b;
    }

    /** The 17 cells of {@link #standard()} in firing order. */
    public static String[] standardCells() {
        return new String[] {
            "A1", "A2", "A3", "A4", "A5",
            "B1", "B2", "B3", "B4",
            "C1", "C2", "C3",
            "D1", "D2", "D3",
            "E1", "E2"
        };
    }
}
