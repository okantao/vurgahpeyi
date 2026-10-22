package com.amiral.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Game rules from one player's point of view: own board, actions against the enemy, turn order and winner.
 * The enemy board is unknown, so the results of our actions are applied when the opponent reports them.
 * A hit (shot or torpedo) keeps the turn; a miss or a radar scan passes it to the opponent.
 */
public class GameEngine {

    private final boolean localIsServer;
    private GameState state = GameState.SETUP;
    private Board local;
    private boolean localReady;
    private boolean remoteReady;
    private boolean localTurn;
    private Coordinate pendingTarget;
    private Weapon pendingWeapon;
    private final Map<Coordinate, ShotResult> myShotResults = new LinkedHashMap<>();
    private final Set<Coordinate> detected = new LinkedHashSet<>();
    private final Set<Coordinate> scanned = new LinkedHashSet<>();
    private final Set<ShipType> enemySunk = EnumSet.noneOf(ShipType.class);
    private final Map<Weapon, Integer> myUses = new EnumMap<>(Weapon.class);
    private final Map<Weapon, Integer> enemyUses = new EnumMap<>(Weapon.class);
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

    // ---------------------------------------------------------------- our actions

    /** Records the local player's shot. The result arrives later via {@link #applyOpponentShotResult}. */
    public void fireAt(Coordinate c) {
        use(Weapon.SHOT, c);
    }

    /** Records a radar scan centred on {@code c}. The result arrives via {@link #applyRadarResult}. */
    public void useRadar(Coordinate c) {
        use(Weapon.RADAR, c);
    }

    /** Records a torpedo aimed at {@code c}. The result arrives via {@link #applyTorpedoResult}. */
    public void fireTorpedo(Coordinate c) {
        use(Weapon.TORPEDO, c);
    }

    /** Uses a weapon on our turn. Throws if it is not our turn, the weapon is used up or nothing new is hit. */
    public void use(Weapon weapon, Coordinate c) {
        requirePlaying();
        if (!localTurn || pendingTarget != null) {
            throw new GameRuleException(RuleViolation.NOT_YOUR_TURN, "Not your turn");
        }
        if (usesLeft(weapon) <= 0) {
            throw new GameRuleException(RuleViolation.NO_ABILITY_LEFT, "No " + weapon.displayName() + " left");
        }
        if (weapon != Weapon.RADAR && weapon.area(c).stream().allMatch(myShotResults::containsKey)) {
            throw new GameRuleException(RuleViolation.ALREADY_SHOT, "Already shot at " + c);
        }
        myUses.merge(weapon, 1, Integer::sum);
        pendingTarget = c;
        pendingWeapon = weapon;
        localTurn = false;
    }

    /** Applies the result of our pending shot as reported by the opponent. */
    public void applyOpponentShotResult(Coordinate c, ShotResult r, ShipType sunk) {
        requirePending(Weapon.SHOT, c);
        if (r == ShotResult.ALREADY_SHOT) {
            throw new GameRuleException(RuleViolation.ALREADY_SHOT, "Already shot at " + c);
        }
        clearPending();
        record(c, r, sunk);
        localTurn = r.isHit();
        checkWin();
    }

    /** Applies the ship cells the opponent reported inside our radar area. */
    public void applyRadarResult(Coordinate c, Set<Coordinate> shipCells) {
        requirePending(Weapon.RADAR, c);
        List<Coordinate> area = Weapon.RADAR.area(c);
        for (Coordinate cell : shipCells) {
            if (!area.contains(cell)) {
                throw new GameRuleException(RuleViolation.INVALID_COORD, cell + " is outside the radar area");
            }
        }
        clearPending();
        scanned.addAll(area);
        detected.addAll(shipCells);
    }

    /** Applies the per-cell results of our torpedo. */
    public void applyTorpedoResult(Coordinate c, List<ShotOutcome> outcomes) {
        requirePending(Weapon.TORPEDO, c);
        List<Coordinate> area = Weapon.TORPEDO.area(c);
        for (ShotOutcome o : outcomes) {
            if (!area.contains(o.coordinate()) || o.result() == ShotResult.ALREADY_SHOT) {
                throw new GameRuleException(RuleViolation.INVALID_COORD, "Bad torpedo result " + o.coordinate());
            }
        }
        clearPending();
        for (ShotOutcome o : outcomes) {
            if (!myShotResults.containsKey(o.coordinate())) {
                record(o.coordinate(), o.result(), o.sunkType());
            }
        }
        localTurn = anyHit(outcomes);
        checkWin();
    }

    /** Rolls back our pending action when the opponent rejects it: the turn and the weapon use come back. */
    public void cancelPendingShot() {
        if (state == GameState.PLAYING && pendingTarget != null) {
            myUses.merge(pendingWeapon, -1, Integer::sum);
            clearPending();
            localTurn = true;
        }
    }

    private void record(Coordinate c, ShotResult r, ShipType sunk) {
        myShotResults.put(c, r);
        if (r.isHit()) {
            enemyHits++;
        }
        if (r == ShotResult.SUNK && sunk != null) {
            enemySunk.add(sunk);
        }
    }

    private void checkWin() {
        if (enemyHits >= ShipType.FLEET_CELLS) {
            finish(true);
        }
    }

    // ---------------------------------------------------------------- enemy actions on our board

    /** Applies an enemy shot to our board. A hit or a repeated shot does not change the turn. */
    public ShotOutcome receiveEnemyShot(Coordinate c) {
        requireEnemyTurn();
        ShotOutcome outcome = local.receiveShot(c);
        if (outcome.result() == ShotResult.ALREADY_SHOT) {
            return outcome;
        }
        endEnemyTurn(outcome.result().isHit());
        return outcome;
    }

    /** Enemy radar: returns our ship cells inside the 4x4 area (they are not hit). */
    public List<Coordinate> receiveEnemyRadar(Coordinate c) {
        requireEnemyTurn();
        requireEnemyUse(Weapon.RADAR);
        List<Coordinate> found = new ArrayList<>();
        for (Coordinate cell : Weapon.RADAR.area(c)) {
            if (local.shipAt(cell).isPresent()) {
                found.add(cell);
            }
        }
        enemyUses.merge(Weapon.RADAR, 1, Integer::sum);
        endEnemyTurn(false);
        return found;
    }

    /** Enemy torpedo: shoots every not yet shot cell of the 3x3 area. */
    public List<ShotOutcome> receiveEnemyTorpedo(Coordinate c) {
        requireEnemyTurn();
        requireEnemyUse(Weapon.TORPEDO);
        List<Coordinate> fresh = Weapon.TORPEDO.area(c).stream().filter(cell -> !local.wasShot(cell)).toList();
        if (fresh.isEmpty()) {
            throw new GameRuleException(RuleViolation.ALREADY_SHOT, "Every cell around " + c + " was already shot");
        }
        List<ShotOutcome> outcomes = new ArrayList<>();
        for (Coordinate cell : fresh) {
            outcomes.add(local.receiveShot(cell));
        }
        enemyUses.merge(Weapon.TORPEDO, 1, Integer::sum);
        endEnemyTurn(anyHit(outcomes));
        return outcomes;
    }

    private void requireEnemyTurn() {
        requirePlaying();
        if (localTurn || pendingTarget != null) {
            throw new GameRuleException(RuleViolation.NOT_YOUR_TURN, "Not the opponent's turn");
        }
    }

    private void requireEnemyUse(Weapon w) {
        if (enemyUsesLeft(w) <= 0) {
            throw new GameRuleException(RuleViolation.NO_ABILITY_LEFT, "No " + w.displayName() + " left");
        }
    }

    /** The opponent keeps the turn after a hit. */
    private void endEnemyTurn(boolean enemyHit) {
        localTurn = !enemyHit;
        if (local.allSunk()) {
            finish(false);
        }
    }

    // ---------------------------------------------------------------- state

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

    private void requirePending(Weapon weapon, Coordinate c) {
        requirePlaying();
        if (pendingWeapon != weapon || !c.equals(pendingTarget)) {
            throw new GameRuleException(RuleViolation.INVALID_COORD, "No pending " + weapon + " at " + c);
        }
    }

    private static boolean anyHit(List<ShotOutcome> outcomes) {
        return outcomes.stream().anyMatch(o -> o.result().isHit());
    }

    private void clearPending() {
        pendingTarget = null;
        pendingWeapon = null;
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

    /** Enemy ship cells revealed by our radars. */
    public Set<Coordinate> detectedShipCells() {
        return Collections.unmodifiableSet(detected);
    }

    /** Every enemy cell covered by our radars (ship or water). */
    public Set<Coordinate> scannedCells() {
        return Collections.unmodifiableSet(scanned);
    }

    /** True if our radar showed that this enemy cell is water. */
    public boolean isKnownWater(Coordinate c) {
        return scanned.contains(c) && !detected.contains(c);
    }

    public Set<ShipType> enemySunk() {
        return Collections.unmodifiableSet(enemySunk);
    }

    public int enemyHits() {
        return enemyHits;
    }

    public int usesLeft(Weapon w) {
        return w == Weapon.SHOT ? Integer.MAX_VALUE : w.uses() - myUses.getOrDefault(w, 0);
    }

    public int enemyUsesLeft(Weapon w) {
        return w == Weapon.SHOT ? Integer.MAX_VALUE : w.uses() - enemyUses.getOrDefault(w, 0);
    }

    public boolean hasShotAt(Coordinate c) {
        return myShotResults.containsKey(c) || (pendingWeapon == Weapon.SHOT && c.equals(pendingTarget));
    }

    /** The weapon of the action waiting for the opponent's answer, or null. */
    public Weapon pendingWeapon() {
        return pendingWeapon;
    }
}
