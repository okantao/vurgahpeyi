package com.amiral.controller;

import java.util.List;

/** Data for the history window. Times are already in local time, {@code MM/dd/yyyy HH:mm:ss}. */
public interface HistoryProvider {

    record Stats(int games, int wins, int losses, int aborted, double winRate) {
    }

    record GameRow(long id, String mode, String started, String ended, String remoteIp, String result,
            String opponent) {
    }

    /** {@code weapon} is SHOT, TORPEDO or RADAR. */
    record MoveRow(int turn, String shooter, String weapon, String coord, String result, String time) {
    }

    Stats stats();

    List<GameRow> games(int limit);

    List<MoveRow> moves(long gameId);
}
