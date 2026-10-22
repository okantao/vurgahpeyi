package com.amiral.model;

/** Thrown when a move breaks a game rule. */
public class GameRuleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final RuleViolation violation;

    public GameRuleException(RuleViolation violation, String message) {
        super(message);
        this.violation = violation;
    }

    public RuleViolation violation() {
        return violation;
    }
}
