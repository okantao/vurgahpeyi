package com.amiral.model;

import java.util.Random;

/** Places a full fleet at random positions. */
public final class RandomFleetGenerator {

    static final int MAX_ATTEMPTS = 1000;

    private RandomFleetGenerator() {
    }

    public static Board generate(Random rnd) {
        Board board = new Board();
        for (ShipType type : ShipType.values()) {
            board.placeShip(randomShip(board, type, rnd));
        }
        return board;
    }

    /** Picks a random position for {@code type} that fits on {@code board}. */
    public static Ship randomShip(Board board, ShipType type, Random rnd) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Orientation o = rnd.nextBoolean() ? Orientation.H : Orientation.V;
            Coordinate start = Coordinate.of(rnd.nextInt(Coordinate.SIZE), rnd.nextInt(Coordinate.SIZE) + 1);
            Ship ship = new Ship(type, start, o);
            if (board.canPlace(ship)) {
                return ship;
            }
        }
        throw new IllegalStateException("Could not place " + type + " after " + MAX_ATTEMPTS + " attempts");
    }
}
