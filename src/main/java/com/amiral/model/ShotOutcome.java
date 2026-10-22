package com.amiral.model;

import java.util.List;

/**
 * Result of a shot together with the ship type and cells that were sunk (only for {@link ShotResult#SUNK}).
 */
public record ShotOutcome(Coordinate coordinate, ShotResult result, ShipType sunkType, List<Coordinate> sunkCells) {

    public ShotOutcome {
        sunkCells = sunkCells == null ? List.of() : List.copyOf(sunkCells);
    }

    public static ShotOutcome of(Coordinate c, ShotResult result) {
        return new ShotOutcome(c, result, null, List.of());
    }
}
