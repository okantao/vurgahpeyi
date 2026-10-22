package com.amiral.model;

/** Reasons a move can be rejected. Names match the protocol ERROR codes. */
public enum RuleViolation {
    INVALID_COORD, NOT_YOUR_TURN, ALREADY_SHOT, INVALID_FLEET, GAME_NOT_STARTED, NO_ABILITY_LEFT, INVALID_STATE
}
