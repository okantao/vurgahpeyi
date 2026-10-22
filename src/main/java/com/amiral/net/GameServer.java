package com.amiral.net;

import com.amiral.util.AppLogger;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Logger;

/**
 * Accepts one client at a time. Further connections get {@code REJECT|ALREADY_CONNECTED}.
 * Handles HELLO/WELCOME and passes every other message to the listener.
 */
public class GameServer implements Connection.Handler {

    public static final String ALREADY_CONNECTED = "ALREADY_CONNECTED";
    private static final Logger LOG = AppLogger.get(GameServer.class);

    private final NetworkListener listener;
    private final String nickname;
    private final long heartbeatMillis;
    private final long timeoutMillis;
    private ServerSocket serverSocket;
    private volatile Connection client;
    private volatile boolean welcomed;

    public GameServer(NetworkListener listener, String nickname) {
        this(listener, nickname, Connection.HEARTBEAT_MILLIS, Connection.TIMEOUT_MILLIS);
    }

    public GameServer(NetworkListener listener, String nickname, long heartbeatMillis, long timeoutMillis) {
        this.listener = listener;
        this.nickname = nickname;
        this.heartbeatMillis = heartbeatMillis;
        this.timeoutMillis = timeoutMillis;
    }

    /** Binds the port (0 lets the system choose) and accepts clients on a background thread. */
    public synchronized void start(int port) throws IOException {
        if (serverSocket != null) {
            throw new IllegalStateException("Server already started");
        }
        serverSocket = new ServerSocket(port);
        ServerSocket socket = serverSocket;
        Thread acceptor = new Thread(() -> acceptLoop(socket), "net-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void acceptLoop(ServerSocket socket) {
        while (!socket.isClosed()) {
            try {
                Socket s = socket.accept();
                handleNewSocket(s);
            } catch (SocketException e) {
                return; // server socket closed by stop()
            } catch (IOException e) {
                listener.onError(e);
            }
        }
    }

    private synchronized void handleNewSocket(Socket s) throws IOException {
        if (client != null && !client.isClosed()) {
            Connection rejected = new Connection(s, new Connection.Handler() {
                @Override
                public void onMessage(Connection c, Message m) {
                }

                @Override
                public void onClosed(Connection c, String reason) {
                }

                @Override
                public void onError(Connection c, Exception e) {
                }
            });
            rejected.send(Message.reject(ALREADY_CONNECTED));
            rejected.close("rejected");
            LOG.info("Rejected second client " + s.getInetAddress());
            return;
        }
        welcomed = false;
        client = new Connection(s, this, heartbeatMillis, timeoutMillis);
        client.start();
    }

    @Override
    public void onMessage(Connection c, Message m) {
        if (c != client) {
            return;
        }
        if (m.type() == MessageType.HELLO) {
            String remoteNick;
            try {
                remoteNick = ProtocolCodec.unb64(m.field(0));
            } catch (ProtocolException e) {
                listener.onError(e);
                remoteNick = "?";
            }
            c.send(Message.welcome(nickname));
            welcomed = true;
            listener.onConnected(remoteNick, c.remoteAddress());
            return;
        }
        if (!welcomed) {
            listener.onError(new ProtocolException("Message before HELLO: " + m.type()));
            return;
        }
        listener.onMessage(m);
    }

    @Override
    public void onClosed(Connection c, String reason) {
        boolean wasWelcomed;
        synchronized (this) {
            if (c != client) {
                return;
            }
            wasWelcomed = welcomed;
            welcomed = false;
        }
        if (wasWelcomed) {
            listener.onDisconnected(reason);
        }
    }

    @Override
    public void onError(Connection c, Exception e) {
        listener.onError(e);
    }

    /** Sends to the connected client; returns false if there is none. */
    public boolean send(Message m) {
        Connection c = client;
        return c != null && welcomed && c.send(m);
    }

    /** Disconnects the current client but keeps listening. */
    public void dropClient(String reason) {
        Connection c = client;
        if (c != null) {
            c.close(reason);
        }
    }

    /** Closes the client and the server socket. */
    public void stop() {
        ServerSocket s;
        synchronized (this) {
            s = serverSocket;
            serverSocket = null;
        }
        dropClient("Server stopped");
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                LOG.fine("Close failed: " + e);
            }
        }
    }

    public synchronized int port() {
        return serverSocket == null ? -1 : serverSocket.getLocalPort();
    }

    public synchronized boolean isListening() {
        return serverSocket != null && !serverSocket.isClosed();
    }

    public boolean isConnected() {
        Connection c = client;
        return c != null && !c.isClosed() && welcomed;
    }
}
