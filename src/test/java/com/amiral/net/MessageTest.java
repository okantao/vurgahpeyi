package com.amiral.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageTest {

    @Test
    void fieldsAreImmutableCopies() {
        List<String> fields = new ArrayList<>(List.of("A1"));
        Message m = new Message(MessageType.SHOOT, fields);
        fields.add("B2");
        assertEquals(1, m.fields().size());
        assertThrows(UnsupportedOperationException.class, () -> m.fields().add("C3"));
    }

    @Test
    void nullFieldsBecomeEmpty() {
        assertTrue(new Message(MessageType.PING, null).fields().isEmpty());
        assertThrows(NullPointerException.class, () -> new Message(null, List.of()));
    }

    @Test
    void factoriesFillExpectedFields() throws ProtocolException {
        assertEquals("true", Message.start(true).field(0));
        assertEquals("LOSE", Message.gameOver(false).field(0));
        assertEquals("", Message.shot("REMOTE", "A1", "MISS", null).field(3));
        assertEquals("Kaan", ProtocolCodec.unb64(Message.hello("Kaan").field(0)));
    }
}
