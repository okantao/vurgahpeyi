package com.amiral.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Chooses the computer's next action from what it knows about the enemy board (its own shot results,
 * radar findings and sunk ship types). It never looks at the opponent's real fleet.
 *
 * <ul>
 *   <li>EASY: random shots, uses radar and torpedo at random places now and then.</li>
 *   <li>NORMAL: hunt and target. Shoots radar finds first, follows hits, hunts on a checkerboard.</li>
 *   <li>HARD: probability map of every possible ship position; radar and torpedo go where they pay off most.</li>
 * </ul>
 */
public class ComputerPlayer {

    public enum Difficulty {
        EASY("Easy"), NORMAL("Normal"), HARD("Hard");

        private final String label;

        Difficulty(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** A chosen action. */
    public record Move(Weapon weapon, Coordinate target) {
    }

    private final Difficulty difficulty;
    private final Random random;
    /** Hit cells that belong to ships already sunk (worked out from the SUNK reports). */
    private final Set<Coordinate> resolved = new HashSet<>();
    private final Set<ShipType> sunkTypes = EnumSet.noneOf(ShipType.class);

    public ComputerPlayer(Difficulty difficulty, Random random) {
        this.difficulty = difficulty;
        this.random = random;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    /**
     * Must be called for every SUNK result so the computer knows which hits are finished.
     *
     * @param results all known shot results, including the sinking one
     */
    public void onSunk(Coordinate c, ShipType type, Map<Coordinate, ShotResult> results) {
        sunkTypes.add(type);
        // The sunk ship is a straight run of hits through c with the ship's length.
        for (Orientation o : Orientation.values()) {
            for (int back = 0; back < type.length(); back++) {
                int dr = o == Orientation.V ? -back : 0;
                int dc = o == Orientation.H ? -back : 0;
                Coordinate start = c.offset(dr, dc).orElse(null);
                if (start == null) {
                    continue;
                }
                Ship candidate = new Ship(type, start, o);
                if (candidate.fitsOnBoard() && candidate.cells().stream().allMatch(
                        cell -> results.getOrDefault(cell, ShotResult.MISS).isHit() && !resolved.contains(cell))) {
                    resolved.addAll(candidate.cells());
                    return;
                }
            }
        }
        resolved.add(c);
    }

    /** Picks the next action. {@code engine} must be in PLAYING state with the computer to move. */
    public Move nextMove(GameEngine engine) {
        Map<Coordinate, ShotResult> results = engine.myShotResults();
        List<Coordinate> open = new ArrayList<>();
        for (int r = 0; r < Coordinate.SIZE; r++) {
            for (int c = 1; c <= Coordinate.SIZE; c++) {
                Coordinate cell = Coordinate.of(r, c);
                if (!results.containsKey(cell) && !engine.isKnownWater(cell)) {
                    open.add(cell);
                }
            }
        }
        if (open.isEmpty()) {
            // Only scanned water is left (cannot really happen while ships are afloat).
            for (int r = 0; r < Coordinate.SIZE && open.isEmpty(); r++) {
                for (int c = 1; c <= Coordinate.SIZE; c++) {
                    if (!results.containsKey(Coordinate.of(r, c))) {
                        open.add(Coordinate.of(r, c));
                    }
                }
            }
        }
        return switch (difficulty) {
            case EASY -> easy(engine, open);
            case NORMAL -> normal(engine, results, open);
            case HARD -> hard(engine, results, open);
        };
    }

    private Move easy(GameEngine engine, List<Coordinate> open) {
        Coordinate any = open.get(random.nextInt(open.size()));
        if (engine.usesLeft(Weapon.TORPEDO) > 0 && random.nextInt(10) == 0) {
            return new Move(Weapon.TORPEDO, any);
        }
        if (engine.usesLeft(Weapon.RADAR) > 0 && random.nextInt(10) == 0) {
            return new Move(Weapon.RADAR, any);
        }
        return new Move(Weapon.SHOT, any);
    }

    private Move normal(GameEngine engine, Map<Coordinate, ShotResult> results, List<Coordinate> open) {
        Coordinate found = firstDetected(engine, results);
        if (found != null) {
            return new Move(Weapon.SHOT, found);
        }
        List<Coordinate> targets = targetCells(engine, results);
        if (!targets.isEmpty()) {
            return new Move(Weapon.SHOT, targets.get(0));
        }
        if (engine.usesLeft(Weapon.RADAR) > 0 && random.nextInt(3) == 0) {
            return new Move(Weapon.RADAR, bestArea(Weapon.RADAR, cell -> unknown(engine, results, cell) ? 1 : 0));
        }
        if (engine.usesLeft(Weapon.TORPEDO) > 0 && random.nextInt(5) == 0) {
            return new Move(Weapon.TORPEDO, bestArea(Weapon.TORPEDO, cell -> unknown(engine, results, cell) ? 1 : 0));
        }
        List<Coordinate> parity = open.stream().filter(c -> (c.rowIndex() + c.col()) % 2 == 0).toList();
        List<Coordinate> pool = parity.isEmpty() ? open : parity;
        return new Move(Weapon.SHOT, pool.get(random.nextInt(pool.size())));
    }

    private Move hard(GameEngine engine, Map<Coordinate, ShotResult> results, List<Coordinate> open) {
        Coordinate found = firstDetected(engine, results);
        if (found != null) {
            return new Move(Weapon.SHOT, found);
        }
        double[][] density = density(engine, results);
        boolean targeting = !unresolvedHits(results).isEmpty();
        if (!targeting && engine.usesLeft(Weapon.RADAR) > 0 && results.size() >= 4) {
            return new Move(Weapon.RADAR, bestArea(Weapon.RADAR, c -> density[c.rowIndex()][c.col() - 1]));
        }
        if (engine.usesLeft(Weapon.TORPEDO) > 0) {
            Coordinate centre = bestArea(Weapon.TORPEDO, c -> density[c.rowIndex()][c.col() - 1]);
            double gain = Weapon.TORPEDO.area(centre).stream().mapToDouble(c -> density[c.rowIndex()][c.col() - 1]).sum();
            double bestCell = open.stream().mapToDouble(c -> density[c.rowIndex()][c.col() - 1]).max().orElse(0);
            if (targeting ? gain > 2.5 * bestCell : gain > 4.5 * bestCell) {
                return new Move(Weapon.TORPEDO, centre);
            }
        }
        Coordinate best = open.stream()
                .max(Comparator.comparingDouble((Coordinate c) -> density[c.rowIndex()][c.col() - 1])
                        .thenComparingInt(c -> random.nextInt(1000)))
                .orElseThrow();
        return new Move(Weapon.SHOT, best);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean unknown(GameEngine engine, Map<Coordinate, ShotResult> results, Coordinate c) {
        return !results.containsKey(c) && !engine.scannedCells().contains(c);
    }

    private static Coordinate firstDetected(GameEngine engine, Map<Coordinate, ShotResult> results) {
        for (Coordinate c : engine.detectedShipCells()) {
            if (!results.containsKey(c)) {
                return c;
            }
        }
        return null;
    }

    private List<Coordinate> unresolvedHits(Map<Coordinate, ShotResult> results) {
        List<Coordinate> hits = new ArrayList<>();
        results.forEach((c, r) -> {
            if (r.isHit() && !resolved.contains(c)) {
                hits.add(c);
            }
        });
        return hits;
    }

    /** Open neighbours of unresolved hits, cells in line with two hits first. */
    private List<Coordinate> targetCells(GameEngine engine, Map<Coordinate, ShotResult> results) {
        List<Coordinate> hits = unresolvedHits(results);
        Set<Coordinate> hitSet = new HashSet<>(hits);
        List<Coordinate> inLine = new ArrayList<>();
        List<Coordinate> around = new ArrayList<>();
        int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (Coordinate h : hits) {
            for (int[] d : dirs) {
                Coordinate next = h.offset(d[0], d[1]).orElse(null);
                if (next == null || results.containsKey(next) || engine.isKnownWater(next)) {
                    continue;
                }
                Coordinate behind = h.offset(-d[0], -d[1]).orElse(null);
                if (behind != null && hitSet.contains(behind)) {
                    inLine.add(next);
                } else {
                    around.add(next);
                }
            }
        }
        List<Coordinate> all = new ArrayList<>(inLine);
        all.addAll(around);
        return all;
    }

    /** For every cell, how many positions of the remaining ships cover it (hits weigh much more). */
    private double[][] density(GameEngine engine, Map<Coordinate, ShotResult> results) {
        double[][] d = new double[Coordinate.SIZE][Coordinate.SIZE];
        Set<Coordinate> detected = engine.detectedShipCells();
        for (ShipType type : ShipType.values()) {
            if (sunkTypes.contains(type)) {
                continue;
            }
            for (int r = 0; r < Coordinate.SIZE; r++) {
                for (int c = 1; c <= Coordinate.SIZE; c++) {
                    for (Orientation o : Orientation.values()) {
                        Ship s = new Ship(type, Coordinate.of(r, c), o);
                        if (!s.fitsOnBoard()) {
                            continue;
                        }
                        double weight = 1;
                        boolean possible = true;
                        for (Coordinate cell : s.cells()) {
                            ShotResult res = results.get(cell);
                            if (res == ShotResult.MISS || resolved.contains(cell) || engine.isKnownWater(cell)) {
                                possible = false;
                                break;
                            }
                            if (res != null && res.isHit()) {
                                weight *= 30;
                            } else if (detected.contains(cell)) {
                                weight *= 30;
                            }
                        }
                        if (!possible) {
                            continue;
                        }
                        for (Coordinate cell : s.cells()) {
                            if (!results.containsKey(cell)) {
                                d[cell.rowIndex()][cell.col() - 1] += weight;
                            }
                        }
                    }
                }
            }
        }
        return d;
    }

    /** The anchor whose area has the largest total score. */
    private Coordinate bestArea(Weapon w, java.util.function.ToDoubleFunction<Coordinate> score) {
        Coordinate best = null;
        double bestScore = -1;
        for (int r = 0; r < Coordinate.SIZE; r++) {
            for (int c = 1; c <= Coordinate.SIZE; c++) {
                Coordinate anchor = Coordinate.of(r, c);
                double s = w.area(anchor).stream().mapToDouble(score).sum() + random.nextDouble() * 0.01;
                if (s > bestScore) {
                    bestScore = s;
                    best = anchor;
                }
            }
        }
        return best;
    }
}
