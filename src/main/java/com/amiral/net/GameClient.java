package com.amiral.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Connects to a {@link GameServer}, performs HELLO/WELCOME and passes messages to the listener. */
public class GameClient implements Connection.Handler {

    public static final int HANDSHAKE_TIMEOUT_MILLIS = 5_000;

    private final NetworkListener listener;
    private final long heartbeatMillis;
    private final long timeoutMillis;
    private volatile Connection connection;
    private volatile boolean welcomed;
    private CompletableFuture<String> handshake;

    public GameClient(NetworkListener listener) {
        this(listener, Connection.HEARTBEAT_MILLIS, Connection.TIMEOUT_MILLIS);
    }

    public GameClient(NetworkListener listener, long heartbeatMillis, long timeoutMillis) {
        this.listener = listener;
        this.heartbeatMillis = heartbeatMillis;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Connects and waits for WELCOME (5 s). Blocking: call it off the Swing EDT.
     *
     * @return the server player's nickname
     * @throws IOException if the connection fails, is rejected or times out
     */
    public String connect(String host, int port, String nickname) throws IOException {
        if (connection != null && !connection.isClosed()) {
            throw new IllegalStateException("Already connected");
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), HANDSHAKE_TIMEOUT_MILLIS);
        handshake = new CompletableFuture<>();
        welcomed = false;
        Connection c = new Connection(socket, this, heartbeatMillis, timeoutMillis);
        connection = c;
        c.start();
        c.send(Message.hello(nickname));
        try {
            return handshake.get(HANDSHAKE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            c.close("handshake timeout");
            throw new IOException("No answer from server (timeout)", e);
        } catch (ExecutionException e) {
            c.close("rejected");
            throw new IOException(e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            c.close("interrupted");
            throw new IOException("Interrupted", e);
        }
    }

    @Override
    public void onMessage(Connection c, Message m) {
        if (c != connection) {
            return;
        }
        if (!welcomed) {
            if (m.type() == MessageType.WELCOME) {
                String serverNick;
                try {
                    serverNick = ProtocolCodec.unb64(m.field(0));
                } catch (ProtocolException e) {
                    serverNick = "?";
                }
                welcomed = true;
                // Notify before completing so onConnected happens before any later message.
                listener.onConnected(serverNick, c.remoteAddress());
                handshake.complete(serverNick);
            } else if (m.type() == MessageType.REJECT) {
                handshake.completeExceptionally(new IOException("Rejected by server: " + m.field(0)));
            }
            return;
        }
        listener.onMessage(m);
    }

    @Override
    public void onClosed(Connection c, String reason) {
        if (c != connection) {
            return;
        }
        if (handshake != null && !handshake.isDone()) {
            handshake.completeExceptionally(new IOException("Connection closed during handshake"));
        }
        if (welcomed) {
            welcomed = false;
            listener.onDisconnected(reason);
        }
    }

    @Override
    public void onError(Connection c, Exception e) {
        listener.onError(e);
    }

    public boolean send(Message m) {
        Connection c = connection;
        return c != null && welcomed && c.send(m);
    }

    public boolean shoot(String coord) {
        return send(Message.shoot(coord));
    }

    public boolean ready(String fleet) {
        return send(Message.ready(fleet));
    }

    public boolean chat(String sender, String body) {
        return send(Message.chat(sender, body));
    }

    /** Sends QUIT and closes the socket. */
    public void disconnect() {
        Connection c = connection;
        if (c != null && !c.isClosed()) {
            c.send(Message.quit());
            c.close("Disconnected");
        }
    }

    public boolean isConnected() {
        Connection c = connection;
        return c != null && !c.isClosed() && welcomed;
    }
}
