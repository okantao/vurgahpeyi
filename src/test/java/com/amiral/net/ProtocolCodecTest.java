package com.amiral.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amiral.model.Board;
import com.amiral.model.Coordinate;
import com.amiral.model.ShotOutcome;
import com.amiral.model.ShotResult;
import com.amiral.model.RandomFleetGenerator;
import com.amiral.model.Ship;
import com.amiral.model.ShipType;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProtocolCodecTest {

    static Stream<Message> allTypes() {
        return Stream.of(
                Message.hello("Kaan"),
                Message.welcome("TestServer"),
                Message.reject("ALREADY_CONNECTED"),
                Message.ready("CARRIER:A1H;DESTROYER:J9H"),
                Message.start(true),
                Message.shoot("B7"),
                Message.shot("LOCAL", "A5", "SUNK", "DESTROYER"),
                Message.result("A5", "HIT", null),
                Message.radar("C3"),
                Message.radarResult("C3", "B2,B3"),
                Message.torpedo("E5"),
                Message.torpedoResult("E5", "D4:MISS:,E5:SUNK:DESTROYER"),
                Message.chat("LOCAL", "hello"),
                Message.gameOver(true),
                Message.quit(),
                Message.of(MessageType.PING),
                Message.of(MessageType.PONG),
                Message.error("NOT_YOUR_TURN", "Wait for your turn"));
    }

    @ParameterizedTest
    @MethodSource("allTypes")
    void roundTripsEveryType(Message m) throws ProtocolException {
        assertEquals(m, ProtocolCodec.decode(ProtocolCodec.encode(m)));
    }

    @Test
    void coversEveryMessageType() {
        assertEquals(MessageType.values().length, allTypes().map(Message::type).distinct().count());
    }

    @Test
    void emptyShipTypeIsKept() throws ProtocolException {
        assertEquals("RESULT|A5|HIT|", ProtocolCodec.encode(Message.result("A5", "HIT", null)));
        Message m = ProtocolCodec.decode("RESULT|A5|HIT|");
        assertEquals(List.of("A5", "HIT", ""), m.fields());
        assertEquals("", m.field(7));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "FOO|1", "SHOOT", "RESULT|A5", "ERROR|X", "shoot|A1"})
    void rejectsMalformedLines(String line) {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(line));
    }

    @Test
    void rejectsNull() {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode(null));
    }

    @Test
    void separatorInsideFieldFails() {
        assertThrows(IllegalArgumentException.class,
                () -> ProtocolCodec.encode(Message.of(MessageType.SHOOT, "A|1")));
        assertThrows(IllegalArgumentException.class,
                () -> ProtocolCodec.encode(Message.of(MessageType.SHOOT, "A\n1")));
    }

    @Test
    void turkishChatSurvivesBase64() throws ProtocolException {
        String text = "Şu ğemi ıssız, öyle ü ç | pipe \n newline";
        Message decoded = ProtocolCodec.decode(ProtocolCodec.encode(Message.chat("REMOTE", text)));
        assertEquals(text, ProtocolCodec.unb64(decoded.field(1)));
        assertFalse(ProtocolCodec.b64(text).contains("|"));
    }

    @Test
    void invalidBase64Fails() {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.unb64("@@@"));
    }

    @Test
    void errorMessageIsSanitized() throws ProtocolException {
        Message m = Message.error("X", "a|b\nc");
        assertEquals("a/b c", ProtocolCodec.decode(ProtocolCodec.encode(m)).field(1));
    }

    @Test
    void fleetRoundTrip() throws ProtocolException {
        Board b = RandomFleetGenerator.generate(new Random(3));
        String text = ProtocolCodec.encodeFleet(b.fleet());
        Map<ShipType, Ship> decoded = ProtocolCodec.decodeFleet(text);
        assertEquals(b.fleet(), decoded);
        assertEquals(5, text.split(";").length);
        assertEquals(0, ProtocolCodec.decodeFleet("").size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CARRIER", "CARRIER:A1", "BOAT:A1H", "CARRIER:K1H", "CARRIER:A1X",
        "CARRIER:A1H;CARRIER:B1H"})
    void invalidFleetFails(String text) {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeFleet(text));
    }

    @Test
    void cellsRoundTrip() throws ProtocolException {
        List<Coordinate> cells = List.of(Coordinate.parse("A1"), Coordinate.parse("J10"));
        assertEquals("A1,J10", ProtocolCodec.encodeCells(cells));
        assertEquals(cells, ProtocolCodec.decodeCells("A1,J10"));
        assertEquals(List.of(), ProtocolCodec.decodeCells(""));
        assertEquals("", ProtocolCodec.encodeCells(List.of()));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeCells("A1,K1"));
    }

    @Test
    void outcomesRoundTrip() throws ProtocolException {
        List<ShotOutcome> list = List.of(
                ShotOutcome.of(Coordinate.parse("D4"), ShotResult.MISS),
                new ShotOutcome(Coordinate.parse("E5"), ShotResult.SUNK, ShipType.DESTROYER, List.of()));
        String text = ProtocolCodec.encodeOutcomes(list);
        assertEquals("D4:MISS:,E5:SUNK:DESTROYER", text);
        assertEquals(list, ProtocolCodec.decodeOutcomes(text));
        assertEquals(List.of(), ProtocolCodec.decodeOutcomes(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"D4:MISS", "D4:BOOM:", "K4:HIT:", "D4:SUNK:BOAT"})
    void invalidOutcomesFail(String text) {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeOutcomes(text));
    }
}
