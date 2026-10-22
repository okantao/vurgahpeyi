package com.amiral.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A ship placed at a start cell with an orientation. */
public record Ship(ShipType type, Coordinate start, Orientation orientation) {

    public Ship {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(orientation, "orientation");
    }

    /** True if every cell of the ship is on the board. */
    public boolean fitsOnBoard() {
        int end = (orientation == Orientation.H ? start.col() : start.rowIndex() + 1) + type.length() - 1;
        return end <= Coordinate.SIZE;
    }

    /**
     * The cells the ship occupies, starting from {@link #start()}.
     *
     * @throws IllegalStateException if the ship sticks out of the board
     */
    public List<Coordinate> cells() {
        if (!fitsOnBoard()) {
            throw new IllegalStateException(type + " at " + start + orientation + " is out of the board");
        }
        List<Coordinate> cells = new ArrayList<>(type.length());
        for (int i = 0; i < type.length(); i++) {
            int dRow = orientation == Orientation.V ? i : 0;
            int dCol = orientation == Orientation.H ? i : 0;
            cells.add(start.offset(dRow, dCol).orElseThrow());
        }
        return Collections.unmodifiableList(cells);
    }

    public boolean occupies(Coordinate c) {
        return fitsOnBoard() && cells().contains(c);
    }

    /** True if every cell of the ship is in {@code hits}. */
    public boolean isSunk(Set<Coordinate> hits) {
        return hits.containsAll(cells());
    }
}
