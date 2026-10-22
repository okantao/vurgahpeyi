package com.amiral.controller;

/** How the current game is played; stored in {@code game.mode}. */
public enum GameMode {
    /** Host over the network. */
    SERVER,
    /** Joined a host over the network. */
    CLIENT,
    /** Against the computer on this machine. */
    AI
}
