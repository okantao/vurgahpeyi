package com.amiral.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;

class ConnectionTest {

    /** Collects handler callbacks. */
    static final class Collector implements Connection.Handler {
        final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<String> closed = new LinkedBlockingQueue<>();
        final BlockingQueue<Exception> errors = new LinkedBlockingQueue<>();

        @Override
        public void onMessage(Connection c, Message m) {
            messages.add(m);
        }

        @Override
        public void onClosed(Connection c, String reason) {
            closed.add(reason);
        }

        @Override
        public void onError(Connection c, Exception e) {
            errors.add(e);
        }
    }

    @Test
    void messagesTravelBothWays() throws Exception {
        try (ServerSocket ss = new ServerSocket(0); Socket client = new Socket("127.0.0.1", ss.getLocalPort());
                Socket server = ss.accept()) {
            Collector a = new Collector();
            Collector b = new Collector();
            Connection ca = new Connection(client, a);
            Connection cb = new Connection(server, b);
            ca.start();
            cb.start();
            assertTrue(ca.send(Message.shoot("A1")));
            assertEquals(Message.shoot("A1"), RecordingListener.await(b.messages));
            cb.send(Message.chat("REMOTE", "çok güzel"));
            assertEquals(Message.chat("REMOTE", "çok güzel"), RecordingListener.await(a.messages));

            ca.close("bye");
            assertEquals("bye", RecordingListener.await(a.closed));
            RecordingListener.await(b.closed);
            assertFalse(ca.send(Message.quit()));
            ca.close("again");
            assertTrue(a.closed.isEmpty(), "onClosed only once");
        }
    }

    @Test
    void malformedLineIsReportedButConnectionStays() throws Exception {
        try (ServerSocket ss = new ServerSocket(0); Socket raw = new Socket("127.0.0.1", ss.getLocalPort());
                Socket server = ss.accept()) {
            Collector c = new Collector();
            Connection conn = new Connection(server, c);
            conn.start();
            PrintWriter out = new PrintWriter(raw.getOutputStream(), true, StandardCharsets.UTF_8);
            out.print("NONSENSE|1\n\nSHOOT|B2\n");
            out.flush();
            assertInstanceOf(ProtocolException.class, RecordingListener.await(c.errors));
            assertEquals(Message.shoot("B2"), RecordingListener.await(c.messages));
            assertFalse(conn.isClosed());
            conn.close("done");
        }
    }

    @Test
    void answersPingAndTimesOutWithoutTraffic() throws Exception {
        try (ServerSocket ss = new ServerSocket(0); Socket raw = new Socket("127.0.0.1", ss.getLocalPort());
                Socket server = ss.accept()) {
            Collector c = new Collector();
            Connection conn = new Connection(server, c, 50, 300);
            conn.start();
            BufferedReader in = new BufferedReader(new InputStreamReader(raw.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(raw.getOutputStream(), true, StandardCharsets.UTF_8);
            assertEquals("PING", in.readLine(), "heartbeat sent");
            out.print("PING\n");
            out.flush();
            String line;
            do {
                line = in.readLine();
            } while ("PING".equals(line));
            assertEquals("PONG", line);
            // Stay silent: the connection must give up.
            assertEquals("timeout", RecordingListener.await(c.closed));
            assertTrue(c.messages.isEmpty(), "PING/PONG are not passed on");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
