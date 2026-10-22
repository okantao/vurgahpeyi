package com.amiral.net;

import static com.amiral.net.RecordingListener.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.BindException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameLoopbackIT {

    private RecordingListener serverEvents;
    private RecordingListener clientEvents;
    private GameServer server;
    private GameClient client;

    @BeforeEach
    void setUp() throws IOException {
        serverEvents = new RecordingListener();
        clientEvents = new RecordingListener();
        server = new GameServer(serverEvents, "TestServer");
        server.start(0);
        client = new GameClient(clientEvents);
    }

    @AfterEach
    void tearDown() {
        client.disconnect();
        server.stop();
    }

    @Test
    void helloWelcomeHandshake() throws Exception {
        assertTrue(server.isListening());
        assertFalse(server.isConnected());
        assertEquals("TestServer", client.connect("127.0.0.1", server.port(), "Kaan"));
        assertEquals("Kaan", await(serverEvents.connected));
        assertEquals("TestServer", await(clientEvents.connected));
        assertTrue(client.isConnected());
        assertTrue(server.isConnected());
    }

    @Test
    void secondClientIsRejected() throws Exception {
        client.connect("127.0.0.1", server.port(), "First");
        await(serverEvents.connected);
        GameClient intruder = new GameClient(new RecordingListener());
        IOException e = assertThrows(IOException.class,
                () -> intruder.connect("127.0.0.1", server.port(), "Second"));
        assertTrue(e.getMessage().contains(GameServer.ALREADY_CONNECTED), e.getMessage());
        assertTrue(client.isConnected(), "first client still connected");
    }

    @Test
    void portInUseFails() {
        GameServer other = new GameServer(new RecordingListener(), "Other");
        assertThrows(BindException.class, () -> other.start(server.port()));
    }

    @Test
    void fullMessageFlow() throws Exception {
        client.connect("127.0.0.1", server.port(), "Kaan");
        await(serverEvents.connected);

        client.ready("CARRIER:A1H");
        assertEquals("CARRIER:A1H", serverEvents.next(MessageType.READY).field(0));
        server.send(Message.ready("CARRIER:B1H"));
        clientEvents.next(MessageType.READY);
        server.send(Message.start(false));
        assertEquals("false", clientEvents.next(MessageType.START).field(0));

        server.send(Message.shoot("A1"));
        assertEquals("A1", clientEvents.next(MessageType.SHOOT).field(0));
        client.send(Message.result("A1", "HIT", null));
        Message result = serverEvents.next(MessageType.RESULT);
        assertEquals("HIT", result.field(1));

        client.shoot("J10");
        serverEvents.next(MessageType.SHOOT);
        server.send(Message.shot("REMOTE", "J10", "SUNK", "DESTROYER"));
        assertEquals("DESTROYER", clientEvents.next(MessageType.SHOT).field(3));

        client.chat("CLIENT", "İyi oyundu | gg");
        assertEquals("İyi oyundu | gg", ProtocolCodec.unb64(serverEvents.next(MessageType.CHAT).field(1)));

        server.send(Message.gameOver(true));
        assertEquals("WIN", clientEvents.next(MessageType.GAME_OVER).field(0));
    }

    @Test
    void quitNotifiesTheOtherSide() throws Exception {
        client.connect("127.0.0.1", server.port(), "Kaan");
        await(serverEvents.connected);
        client.disconnect();
        serverEvents.next(MessageType.QUIT);
        await(serverEvents.disconnected);
        assertFalse(client.isConnected());
        assertTrue(server.isListening(), "server keeps listening");

        GameClient again = new GameClient(clientEvents);
        again.connect("127.0.0.1", server.port(), "Back");
        assertEquals("Back", await(serverEvents.connected));
        again.disconnect();
    }

    @Test
    void stopServerDisconnectsClient() throws Exception {
        client.connect("127.0.0.1", server.port(), "Kaan");
        await(serverEvents.connected);
        server.stop();
        await(serverEvents.disconnected);
        await(clientEvents.disconnected);
        assertFalse(server.isListening());
        assertFalse(server.send(Message.quit()));
    }

    @Test
    void connectToClosedPortFails() {
        int port = server.port();
        server.stop();
        assertThrows(IOException.class, () -> client.connect("127.0.0.1", port, "Kaan"));
    }
}
