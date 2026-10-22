package com.amiral.model;

/** Outcome of a single shot. */
public enum ShotResult {
    MISS, HIT, SUNK, ALREADY_SHOT;

    public boolean isHit() {
        return this == HIT || this == SUNK;
    }
}
