package com.amiral.net;

/** Protocol message types with the number of fields each one requires. */
public enum MessageType {
    HELLO(1),
    WELCOME(1),
    REJECT(1),
    READY(1),
    START(1),
    SHOOT(1),
    SHOT(4),
    RESULT(3),
    CHAT(2),
    GAME_OVER(1),
    QUIT(0),
    PING(0),
    PONG(0),
    ERROR(2);

    private final int fieldCount;

    MessageType(int fieldCount) {
        this.fieldCount = fieldCount;
    }

    public int fieldCount() {
        return fieldCount;
    }
}
