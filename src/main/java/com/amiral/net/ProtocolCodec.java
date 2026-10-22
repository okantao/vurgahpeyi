package com.amiral.net;

import com.amiral.model.Coordinate;
import com.amiral.model.Orientation;
import com.amiral.model.Ship;
import com.amiral.model.ShipType;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Encodes and decodes single-line, '|' separated protocol messages. */
public final class ProtocolCodec {

    public static final String SEPARATOR = "|";

    private ProtocolCodec() {
    }

    public static String encode(Message m) {
        StringJoiner line = new StringJoiner(SEPARATOR);
        line.add(m.type().name());
        for (String field : m.fields()) {
            if (field.contains(SEPARATOR) || field.contains("\n") || field.contains("\r")) {
                throw new IllegalArgumentException("Field contains a separator or line break: " + field);
            }
            line.add(field);
        }
        return line.toString();
    }

    public static Message decode(String line) throws ProtocolException {
        if (line == null || line.isBlank()) {
            throw new ProtocolException("Empty line");
        }
        String[] parts = line.strip().split("\\|", -1);
        MessageType type;
        try {
            type = MessageType.valueOf(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("Unknown message type: " + parts[0], e);
        }
        List<String> fields = Arrays.asList(parts).subList(1, parts.length);
        if (fields.size() < type.fieldCount()) {
            throw new ProtocolException(type + " needs " + type.fieldCount() + " fields but got " + fields.size());
        }
        return new Message(type, fields);
    }

    /** URL-safe Base64 without padding, so the result never contains '|'. */
    public static String b64(String plain) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    public static String unb64(String encoded) throws ProtocolException {
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("Invalid Base64: " + encoded, e);
        }
    }

    /** Format: {@code CARRIER:A1H;BATTLESHIP:C3V;...}. */
    public static String encodeFleet(Map<ShipType, Ship> fleet) {
        StringJoiner joiner = new StringJoiner(";");
        for (Ship s : fleet.values()) {
            joiner.add(s.type().name() + ":" + s.start() + s.orientation().name());
        }
        return joiner.toString();
    }

    public static Map<ShipType, Ship> decodeFleet(String text) throws ProtocolException {
        Map<ShipType, Ship> fleet = new EnumMap<>(ShipType.class);
        if (text == null || text.isBlank()) {
            return fleet;
        }
        for (String entry : text.split(";")) {
            String[] kv = entry.split(":", -1);
            if (kv.length != 2 || kv[1].length() < 3) {
                throw new ProtocolException("Invalid fleet entry: " + entry);
            }
            try {
                ShipType type = ShipType.valueOf(kv[0]);
                String pos = kv[1];
                Orientation o = Orientation.valueOf(pos.substring(pos.length() - 1));
                Coordinate start = Coordinate.parse(pos.substring(0, pos.length() - 1));
                if (fleet.put(type, new Ship(type, start, o)) != null) {
                    throw new ProtocolException("Duplicate ship: " + type);
                }
            } catch (IllegalArgumentException e) {
                throw new ProtocolException("Invalid fleet entry: " + entry, e);
            }
        }
        return fleet;
    }
}
