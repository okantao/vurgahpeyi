package com.amiral.model;

import java.util.Optional;

/**
 * A cell on the 10x10 board. Rows are A-J, columns are 1-10.
 */
public record Coordinate(char row, int col) {

    public static final int SIZE = 10;

    public Coordinate {
        if (row < 'A' || row > 'J') {
            throw new IllegalArgumentException("Row out of range: " + row);
        }
        if (col < 1 || col > SIZE) {
            throw new IllegalArgumentException("Column out of range: " + col);
        }
    }

    /** Parses text such as "A1" or "j10" (case-insensitive). */
    public static Coordinate parse(String s) {
        if (s == null) {
            throw new IllegalArgumentException("Coordinate is null");
        }
        String t = s.trim().toUpperCase();
        if (!t.matches("[A-J](10|[1-9])")) {
            throw new IllegalArgumentException("Invalid coordinate: '" + s + "'");
        }
        return new Coordinate(t.charAt(0), Integer.parseInt(t.substring(1)));
    }

    /** Builds a coordinate from a 0-based row index (0-9) and a 1-based column (1-10). */
    public static Coordinate of(int rowIndex, int col) {
        if (rowIndex < 0 || rowIndex >= SIZE) {
            throw new IllegalArgumentException("Row index out of range: " + rowIndex);
        }
        return new Coordinate((char) ('A' + rowIndex), col);
    }

    /** 0-based row index. */
    public int rowIndex() {
        return row - 'A';
    }

    /** Returns the shifted coordinate, or empty if it falls outside the board. */
    public Optional<Coordinate> offset(int dRow, int dCol) {
        int r = rowIndex() + dRow;
        int c = col + dCol;
        if (r < 0 || r >= SIZE || c < 1 || c > SIZE) {
            return Optional.empty();
        }
        return Optional.of(of(r, c));
    }

    @Override
    public String toString() {
        return row + Integer.toString(col);
    }
}
