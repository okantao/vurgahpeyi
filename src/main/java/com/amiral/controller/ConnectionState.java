package com.amiral.controller;

/** Connection life cycle shown by the Connection Configuration box. */
public enum ConnectionState {
    /** No server running, no client connection. */
    IDLE,
    /** Client mode: connecting / waiting for WELCOME. */
    CONNECTING,
    /** Server mode: listening, no opponent yet. */
    LISTENING,
    /** Opponent connected, no game running (placing ships or game over). */
    CONNECTED,
    /** Game running (both ready or one side waiting). */
    IN_GAME
}
