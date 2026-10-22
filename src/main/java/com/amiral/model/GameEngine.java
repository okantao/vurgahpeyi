package com.amiral.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Game rules from one player's point of view: own board, shots at the enemy, turn order and winner.
 * The enemy board is unknown, so the results of our shots are applied when the opponent reports them.
 */
public class GameEngine {

    private final boolean localIsServer;
    private GameState state = GameState.SETUP;
    private Board local;
    private boolean localReady;
    private boolean remoteReady;
    private boolean localTurn;
    private Coordinate pendingShot;
    private final Map<Coordinate, ShotResult> myShotResults = new LinkedHashMap<>();
    private final Set<ShipType> enemySunk = EnumSet.noneOf(ShipType.class);
    private int enemyHits;
    private Boolean localWon;

    public GameEngine(boolean localIsServer) {
        this.localIsServer = localIsServer;
    }

    public void setLocalFleet(Board b) {
        requireState(GameState.SETUP);
        Objects.requireNonNull(b, "board");
        if (!b.isFleetComplete()) {
            throw new GameRuleException(RuleViolation.INVALID_FLEET, "Fleet is not complete");
        }
        this.local = b;
    }

    /** Marks one side as ready. When both are ready the game starts and the server moves first. */
    public void markReady(boolean isLocal) {
        if (state != GameState.SETUP && state != GameState.WAITING) {
            throw new GameRuleException(RuleViolation.INVALID_STATE, "Cannot get ready in state " + state);
        }
        if (isLocal) {
            if (local == null) {
                throw new GameRuleException(RuleViolation.INVALID_FLEET, "Local fleet is not set");
            }
            localReady = true;
        } else {
            remoteReady = true;
        }
        if (localReady && remoteReady) {
            state = GameState.PLAYING;
            localTurn = localIsServer;
        } else if (localReady) {
            state = GameState.WAITING;
        }
    }

    /** Records the local player's shot. The result arrives later via {@link #applyOpponentShotResult}. */
    public void fireAt(Coordinate c) {
        requirePlaying();
        if (!localTurn) {
            throw new GameRuleException(RuleViolation.NOT_YOUR_TURN, "Not your turn");
        }
        if (hasShotAt(c)) {
            throw new GameRuleException(RuleViolation.ALREADY_SHOT, "Already shot at " + c);
        }
        pendingShot = c;
        localTurn = false;
    }

    /** Applies the result of our pending shot as reported by the opponent. */
    public void applyOpponentShotResult(Coordinate c, ShotResult r, ShipType sunk) {
        requirePlaying();
        if (!c.equals(pendingShot)) {
            throw new GameRuleException(RuleViolation.INVALID_COORD, "No pending shot at " + c);
        }
        if (r == ShotResult.ALREADY_SHOT) {
            throw new GameRuleException(RuleViolation.ALREADY_SHOT, "Already shot at " + c);
        }
        pendingShot = null;
        myShotResults.put(c, r);
        if (r.isHit()) {
            enemyHits++;
        }
        if (r == ShotResult.SUNK && sunk != null) {
            enemySunk.add(sunk);
        }
        if (enemyHits >= ShipType.FLEET_CELLS) {
            finish(true);
        }
    }

    /**
     * Rolls back our pending shot when the opponent rejects it (e.g. ERROR NOT_YOUR_TURN): the turn stays ours.
     */
    public void cancelPendingShot() {
        if (state == GameState.PLAYING && pendingShot != null) {
            pendingShot = null;
            localTurn = true;
        }
    }

    /** Applies an enemy shot to our board. A repeated shot does not change the turn. */
    public ShotOutcome receiveEnemyShot(Coordinate c) {
        requirePlaying();
        if (localTurn || pendingShot != null) {
            throw new GameRuleException(RuleViolation.NOT_YOUR_TURN, "Not the opponent's turn");
        }
        ShotOutcome outcome = local.receiveShot(c);
        if (outcome.result() == ShotResult.ALREADY_SHOT) {
            return outcome;
        }
        localTurn = true;
        if (local.allSunk()) {
            finish(false);
        }
        return outcome;
    }

    public void abort() {
        if (state != GameState.FINISHED) {
            state = GameState.ABORTED;
            localTurn = false;
        }
    }

    /** {@code Optional.of(true)} if we won, {@code Optional.of(false)} if the opponent won, empty otherwise. */
    public Optional<Boolean> winner() {
        return Optional.ofNullable(localWon);
    }

    private void finish(boolean won) {
        localWon = won;
        state = GameState.FINISHED;
        localTurn = false;
    }

    private void requirePlaying() {
        if (state != GameState.PLAYING) {
            throw new GameRuleException(RuleViolation.GAME_NOT_STARTED, "Game is not running (" + state + ")");
        }
    }

    private void requireState(GameState expected) {
        if (state != expected) {
            throw new GameRuleException(RuleViolation.INVALID_STATE, "Expected " + expected + " but was " + state);
        }
    }

    public GameState state() {
        return state;
    }

    public boolean isLocalTurn() {
        return state == GameState.PLAYING && localTurn;
    }

    public boolean isLocalReady() {
        return localReady;
    }

    public boolean isRemoteReady() {
        return remoteReady;
    }

    public boolean isLocalServer() {
        return localIsServer;
    }

    public Board localBoard() {
        return local;
    }

    public Map<Coordinate, ShotResult> myShotResults() {
        return Collections.unmodifiableMap(myShotResults);
    }

    public Set<ShipType> enemySunk() {
        return Collections.unmodifiableSet(enemySunk);
    }

    public int enemyHits() {
        return enemyHits;
    }

    public boolean hasShotAt(Coordinate c) {
        return myShotResults.containsKey(c) || c.equals(pendingShot);
    }
}
