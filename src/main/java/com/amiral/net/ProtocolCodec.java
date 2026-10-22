package com.amiral.net;

import com.amiral.model.Coordinate;
import com.amiral.model.Orientation;
import com.amiral.model.Ship;
import com.amiral.model.ShipType;
import com.amiral.model.ShotOutcome;
import com.amiral.model.ShotResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    /** Cells as {@code A1,B2,C3}; empty string for none. */
    public static String encodeCells(List<Coordinate> cells) {
        StringJoiner joiner = new StringJoiner(",");
        cells.forEach(c -> joiner.add(c.toString()));
        return joiner.toString();
    }

    public static List<Coordinate> decodeCells(String text) throws ProtocolException {
        List<Coordinate> cells = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return cells;
        }
        for (String part : text.split(",")) {
            try {
                cells.add(Coordinate.parse(part));
            } catch (IllegalArgumentException e) {
                throw new ProtocolException("Invalid cell: " + part, e);
            }
        }
        return cells;
    }

    /** Shot outcomes as {@code A1:HIT:,A2:SUNK:DESTROYER}. Sunk cells are not sent. */
    public static String encodeOutcomes(List<ShotOutcome> outcomes) {
        StringJoiner joiner = new StringJoiner(",");
        for (ShotOutcome o : outcomes) {
            joiner.add(o.coordinate() + ":" + o.result().name() + ":" + (o.sunkType() == null ? "" : o.sunkType().name()));
        }
        return joiner.toString();
    }

    public static List<ShotOutcome> decodeOutcomes(String text) throws ProtocolException {
        List<ShotOutcome> list = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return list;
        }
        for (String part : text.split(",")) {
            String[] f = part.split(":", -1);
            if (f.length != 3) {
                throw new ProtocolException("Invalid outcome: " + part);
            }
            try {
                ShipType type = f[2].isEmpty() ? null : ShipType.valueOf(f[2]);
                list.add(new ShotOutcome(Coordinate.parse(f[0]), ShotResult.valueOf(f[1]), type, List.of()));
            } catch (IllegalArgumentException e) {
                throw new ProtocolException("Invalid outcome: " + part, e);
            }
        }
        return list;
    }
}
