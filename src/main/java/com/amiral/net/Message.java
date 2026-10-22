package com.amiral.net;

import java.util.List;
import java.util.Objects;

/** A protocol message: a type and its positional fields. */
public record Message(MessageType type, List<String> fields) {

    public Message {
        Objects.requireNonNull(type, "type");
        fields = List.copyOf(fields == null ? List.of() : fields);
    }

    public static Message of(MessageType type, String... fields) {
        return new Message(type, List.of(fields));
    }

    /** The field at {@code index}, or an empty string if it is missing. */
    public String field(int index) {
        return index < fields.size() ? fields.get(index) : "";
    }

    // Convenience factories for the messages of section 5.1.

    public static Message hello(String nickname) {
        return of(MessageType.HELLO, ProtocolCodec.b64(nickname));
    }

    public static Message welcome(String nickname) {
        return of(MessageType.WELCOME, ProtocolCodec.b64(nickname));
    }

    public static Message reject(String reason) {
        return of(MessageType.REJECT, reason);
    }

    public static Message ready(String fleet) {
        return of(MessageType.READY, fleet);
    }

    public static Message start(boolean yourTurn) {
        return of(MessageType.START, Boolean.toString(yourTurn));
    }

    public static Message shoot(String coord) {
        return of(MessageType.SHOOT, coord);
    }

    public static Message shot(String shooter, String coord, String result, String shipType) {
        return of(MessageType.SHOT, shooter, coord, result, shipType == null ? "" : shipType);
    }

    public static Message result(String coord, String result, String shipType) {
        return of(MessageType.RESULT, coord, result, shipType == null ? "" : shipType);
    }

    public static Message radar(String coord) {
        return of(MessageType.RADAR, coord);
    }

    /** {@code cells} = ship cells found in the 4x4 area, comma separated (may be empty). */
    public static Message radarResult(String coord, String cells) {
        return of(MessageType.RADAR_RESULT, coord, cells);
    }

    public static Message torpedo(String coord) {
        return of(MessageType.TORPEDO, coord);
    }

    /** {@code outcomes} = {@code A1:HIT:,A2:SUNK:DESTROYER,...} for every newly shot cell. */
    public static Message torpedoResult(String coord, String outcomes) {
        return of(MessageType.TORPEDO_RESULT, coord, outcomes);
    }

    public static Message chat(String sender, String body) {
        return of(MessageType.CHAT, sender, ProtocolCodec.b64(body));
    }

    public static Message gameOver(boolean youWin) {
        return of(MessageType.GAME_OVER, youWin ? "WIN" : "LOSE");
    }

    public static Message quit() {
        return of(MessageType.QUIT);
    }

    public static Message error(String code, String message) {
        return of(MessageType.ERROR, code, message.replace('|', '/').replace('\n', ' '));
    }
}
