package com.amiral.controller;

import com.amiral.model.Coordinate;
import com.amiral.model.ShipType;
import com.amiral.model.ShotOutcome;
import com.amiral.model.ShotResult;
import java.util.List;

/** Everything the controller tells the UI. Delivered on the Swing EDT. */
public sealed interface AppEvent {

    /** A line for the Output panel (already stored in app_log). */
    record Log(String message) implements AppEvent {
    }

    /** New text for the status bar. */
    record Status(String text) implements AppEvent {
    }

    record ConnectionChanged(ConnectionState state) implements AppEvent {
    }

    record ServerStarted(String ip, int port) implements AppEvent {
    }

    /** The opponent joined (server) or we joined (client). A new round starts. */
    record ClientConnected(String nickname, String address) implements AppEvent {
    }

    /** Our fleet was accepted and sent. */
    record ShipsReady() implements AppEvent {
    }

    record GameStarted(boolean yourTurn) implements AppEvent {
    }

    record TurnChanged(boolean yourTurn) implements AppEvent {
    }

    /** Result of our shot on the enemy board. */
    record ShotFired(Coordinate coordinate, ShotResult result, ShipType sunkType, List<Coordinate> sunkCells)
            implements AppEvent {
    }

    /** The enemy's shot on our board. */
    record ShotReceived(Coordinate coordinate, ShotResult result, ShipType sunkType, List<Coordinate> sunkCells)
            implements AppEvent {
    }

    /** A radar scan. {@code mine}: we scanned the enemy board; otherwise the enemy scanned ours. */
    record RadarScan(Coordinate anchor, List<Coordinate> area, List<Coordinate> shipCells, boolean mine)
            implements AppEvent {
    }

    /** A torpedo strike with one outcome per newly shot cell (SUNK outcomes carry the ship cells). */
    record TorpedoStrike(Coordinate anchor, List<Coordinate> area, List<ShotOutcome> outcomes, boolean mine)
            implements AppEvent {
    }

    /** Radars and torpedoes left for both players. */
    record AbilitiesChanged(int myRadars, int myTorpedoes, int enemyRadars, int enemyTorpedoes)
            implements AppEvent {
    }

    record ChatReceived(String sender, String body, boolean local) implements AppEvent {
    }

    record GameOver(boolean won) implements AppEvent {
    }

    /** A new round with the same opponent: clear the boards and unlock placement. */
    record RoundReset() implements AppEvent {
    }

    /** The connection ended. {@code opponentLeft} is true when the other side left or vanished during a game. */
    record Disconnected(String reason, boolean opponentLeft) implements AppEvent {
    }

    /** Something went wrong; {@code dialog} asks the UI for an error dialog. */
    record Error(String message, boolean dialog) implements AppEvent {
    }
}
