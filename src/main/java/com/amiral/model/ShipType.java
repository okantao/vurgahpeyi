package com.amiral.model;

/** The five ships of the classic fleet (17 cells in total). */
public enum ShipType {
    CARRIER("Carrier", 5),
    BATTLESHIP("Battleship", 4),
    CRUISER("Cruiser", 3),
    SUBMARINE("Submarine", 3),
    DESTROYER("Destroyer", 2);

    public static final int FLEET_CELLS = 17;

    private final String displayName;
    private final int length;

    ShipType(String displayName, int length) {
        this.displayName = displayName;
        this.length = length;
    }

    public String displayName() {
        return displayName;
    }

    public int length() {
        return length;
    }

    /** Label used in the UI, e.g. "Carrier (5)". */
    public String label() {
        return displayName + " (" + length + ")";
    }
}
