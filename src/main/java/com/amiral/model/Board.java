package com.amiral.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** A player's own board: ship placement and the shots received. */
public class Board {

    private final Map<Coordinate, Ship> cells = new HashMap<>();
    private final Set<Coordinate> shots = new HashSet<>();
    private final List<Ship> ships = new ArrayList<>();

    /** True if the ship fits on the board, does not overlap and its type is not placed yet. */
    public boolean canPlace(Ship s) {
        if (s == null || !s.fitsOnBoard()) {
            return false;
        }
        if (shipOf(s.type()).isPresent()) {
            return false;
        }
        for (Coordinate c : s.cells()) {
            if (cells.containsKey(c)) {
                return false;
            }
        }
        return true;
    }

    public void placeShip(Ship s) {
        if (!canPlace(s)) {
            throw new IllegalArgumentException("Cannot place " + s);
        }
        ships.add(s);
        for (Coordinate c : s.cells()) {
            cells.put(c, s);
        }
    }

    /** Removes the ship of the given type before any shot. Returns true if something was removed. */
    public boolean removeShip(ShipType type) {
        Optional<Ship> ship = shipOf(type);
        if (ship.isEmpty() || !shots.isEmpty()) {
            return false;
        }
        ships.remove(ship.get());
        ship.get().cells().forEach(cells::remove);
        return true;
    }

    public void clear() {
        ships.clear();
        cells.clear();
        shots.clear();
    }

    /** Five ships covering 17 cells. */
    public boolean isFleetComplete() {
        return ships.size() == ShipType.values().length && cells.size() == ShipType.FLEET_CELLS;
    }

    public ShotOutcome receiveShot(Coordinate c) {
        if (shots.contains(c)) {
            return ShotOutcome.of(c, ShotResult.ALREADY_SHOT);
        }
        shots.add(c);
        Ship ship = cells.get(c);
        if (ship == null) {
            return ShotOutcome.of(c, ShotResult.MISS);
        }
        if (ship.isSunk(shots)) {
            return new ShotOutcome(c, ShotResult.SUNK, ship.type(), ship.cells());
        }
        return ShotOutcome.of(c, ShotResult.HIT);
    }

    /** All ship cells have been hit (and the board has a fleet). */
    public boolean allSunk() {
        return !cells.isEmpty() && shots.containsAll(cells.keySet());
    }

    /** The type of the sunk ship covering {@code c}, if any. */
    public Optional<ShipType> sunkShipAt(Coordinate c) {
        Ship ship = cells.get(c);
        if (ship == null || !ship.isSunk(shots)) {
            return Optional.empty();
        }
        return Optional.of(ship.type());
    }

    public Optional<Ship> shipAt(Coordinate c) {
        return Optional.ofNullable(cells.get(c));
    }

    public Optional<Ship> shipOf(ShipType type) {
        return ships.stream().filter(s -> s.type() == type).findFirst();
    }

    public boolean wasShot(Coordinate c) {
        return shots.contains(c);
    }

    public List<Ship> ships() {
        return Collections.unmodifiableList(ships);
    }

    public Set<Coordinate> shots() {
        return Collections.unmodifiableSet(shots);
    }

    /** Ships keyed by type, in enum order. */
    public Map<ShipType, Ship> fleet() {
        Map<ShipType, Ship> fleet = new EnumMap<>(ShipType.class);
        ships.forEach(s -> fleet.put(s.type(), s));
        return fleet;
    }

    /** Builds a board from a fleet map; throws if any ship is invalid. */
    public static Board of(Map<ShipType, Ship> fleet) {
        Board b = new Board();
        fleet.values().forEach(b::placeShip);
        return b;
    }

    /** A copy of the placement without shots. */
    public Board copyPlacement() {
        return of(fleet());
    }
}
