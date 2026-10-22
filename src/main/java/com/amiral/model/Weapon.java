package com.amiral.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a player can use on their turn. A normal shot is unlimited; each player has 3 radars (4x4 scan)
 * and 2 torpedoes (3x3 strike). Using any weapon ends the turn.
 */
public enum Weapon {
    SHOT("Shot", 1, Integer.MAX_VALUE),
    RADAR("Radar", 4, 3),
    TORPEDO("Torpedo", 3, 2);

    private final String displayName;
    private final int size;
    private final int uses;

    Weapon(String displayName, int size, int uses) {
        this.displayName = displayName;
        this.size = size;
        this.uses = uses;
    }

    public String displayName() {
        return displayName;
    }

    /** Side length of the square area. */
    public int size() {
        return size;
    }

    /** Uses per player and game. */
    public int uses() {
        return uses;
    }

    /**
     * The cells covered when aimed at {@code anchor}. The anchor is the centre of the area (for the 4x4 radar,
     * the upper-left of the four centre cells). The area is shifted, not cut, at the edges, so it always has
     * size x size cells.
     */
    public List<Coordinate> area(Coordinate anchor) {
        if (size == 1) {
            return List.of(anchor);
        }
        int back = (size - 1) / 2;
        int top = clamp(anchor.rowIndex() - back);
        int left = clamp(anchor.col() - 1 - back);
        List<Coordinate> cells = new ArrayList<>(size * size);
        for (int r = top; r < top + size; r++) {
            for (int c = left; c < left + size; c++) {
                cells.add(Coordinate.of(r, c + 1));
            }
        }
        return Collections.unmodifiableList(cells);
    }

    private int clamp(int start) {
        return Math.max(0, Math.min(Coordinate.SIZE - size, start));
    }
}
