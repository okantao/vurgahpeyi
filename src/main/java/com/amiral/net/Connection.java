package com.amiral.net;

import com.amiral.util.AppLogger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * One TCP connection: a daemon reader thread, synchronized sending and a PING/PONG heartbeat.
 * PING and PONG are handled here and never reach the handler.
 */
public class Connection {

    public static final long HEARTBEAT_MILLIS = 5_000;
    public static final long TIMEOUT_MILLIS = 15_000;

    /** Receives what the connection reads. */
    public interface Handler {
        void onMessage(Connection c, Message m);

        void onClosed(Connection c, String reason);

        void onError(Connection c, Exception e);
    }

    private static final Logger LOG = AppLogger.get(Connection.class);

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final Handler handler;
    private final long heartbeatMillis;
    private final long timeoutMillis;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile long lastReceived = System.currentTimeMillis();
    private ScheduledExecutorService heartbeat;

    public Connection(Socket socket, Handler handler) throws IOException {
        this(socket, handler, HEARTBEAT_MILLIS, TIMEOUT_MILLIS);
    }

    public Connection(Socket socket, Handler handler, long heartbeatMillis, long timeoutMillis) throws IOException {
        this.socket = socket;
        this.handler = handler;
        this.heartbeatMillis = heartbeatMillis;
        this.timeoutMillis = timeoutMillis;
        socket.setTcpNoDelay(true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    /** Starts the reader thread and the heartbeat. */
    public void start() {
        Thread reader = new Thread(this::readLoop, "net-reader-" + remoteAddress());
        reader.setDaemon(true);
        reader.start();
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "net-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleAtFixedRate(this::beat, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
    }

    private void beat() {
        if (System.currentTimeMillis() - lastReceived > timeoutMillis) {
            close("timeout");
            return;
        }
        send(Message.of(MessageType.PING));
    }

    private void readLoop() {
        String reason = "Connection closed by peer";
        try {
            String line;
            while ((line = in.readLine()) != null) {
                lastReceived = System.currentTimeMillis();
                if (line.isBlank()) {
                    continue;
                }
                Message m;
                try {
                    m = ProtocolCodec.decode(line);
                } catch (ProtocolException e) {
                    handler.onError(this, e);
                    continue;
                }
                if (m.type() == MessageType.PING) {
                    send(Message.of(MessageType.PONG));
                } else if (m.type() != MessageType.PONG) {
                    handler.onMessage(this, m);
                }
            }
        } catch (IOException e) {
            reason = closed.get() ? "closed" : "Connection lost: " + e.getMessage();
        } catch (RuntimeException e) {
            LOG.warning("Handler failed: " + e);
            handler.onError(this, e);
            reason = "Internal error: " + e.getMessage();
        }
        close(reason);
    }

    /** Sends a message; returns false if the connection is closed or the write failed. */
    public synchronized boolean send(Message m) {
        if (closed.get()) {
            return false;
        }
        out.print(ProtocolCodec.encode(m));
        out.print('\n');
        out.flush();
        return !out.checkError();
    }

    /** Closes the socket. The handler's onClosed is called exactly once. */
    public void close(String reason) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (heartbeat != null) {
            heartbeat.shutdownNow();
        }
        try {
            socket.close();
        } catch (IOException e) {
            LOG.fine("Close failed: " + e);
        }
        handler.onClosed(this, reason);
    }

    public boolean isClosed() {
        return closed.get();
    }

    public String remoteAddress() {
        return socket.getInetAddress() == null ? "?" : socket.getInetAddress().getHostAddress();
    }
}
