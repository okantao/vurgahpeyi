package com.amiral.controller;

import com.amiral.net.Message;
import com.amiral.net.NetworkListener;
import java.io.IOException;
import java.net.BindException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-memory network for two controllers. Messages are queued and delivered by {@link #pump()},
 * so tests decide exactly when the other side reacts.
 */
final class FakeNetwork {

    static final int BUSY_PORT = 8080;

    final Deque<Runnable> queue = new ArrayDeque<>();
    final Gateway server = new Gateway(true);
    final Gateway client = new Gateway(false);

    void pump() {
        Runnable r;
        while ((r = queue.poll()) != null) {
            r.run();
        }
    }

    final class Gateway implements NetworkGateway {
        final boolean isServer;
        final List<Message> sent = new ArrayList<>();
        NetworkListener listener;
        String nickname;
        boolean listening;
        boolean connected;

        Gateway(boolean isServer) {
            this.isServer = isServer;
        }

        Gateway peer() {
            return isServer ? client : server;
        }

        @Override
        public void startServer(int port, String nick, NetworkListener l) throws IOException {
            if (port == BUSY_PORT) {
                throw new BindException("Address already in use");
            }
            listener = l;
            nickname = nick;
            listening = true;
        }

        @Override
        public void connect(String host, int port, String nick, NetworkListener l) throws IOException {
            if (!server.listening || server.connected) {
                throw new IOException("Connection refused");
            }
            listener = l;
            nickname = nick;
            connected = true;
            server.connected = true;
            server.listener.onConnected(nick, "127.0.0.1");
            listener.onConnected(server.nickname, "127.0.0.1");
        }

        @Override
        public boolean send(Message m) {
            if (!connected) {
                return false;
            }
            sent.add(m);
            Gateway target = peer();
            queue.add(() -> target.listener.onMessage(m));
            return true;
        }

        @Override
        public void close() {
            if (connected) {
                send(Message.quit());
                dropConnection("Connection closed by peer");
            }
            listening = false;
        }

        @Override
        public void dropClient() {
            if (isServer && connected) {
                dropConnection("Dropped by server");
            }
        }

        private void dropConnection(String peerReason) {
            Gateway other = peer();
            connected = false;
            other.connected = false;
            queue.add(() -> other.listener.onDisconnected(peerReason));
            listener.onDisconnected("closed");
        }

        /** Simulates a broken cable: both sides see a lost connection. */
        void cut() {
            Gateway other = peer();
            connected = false;
            other.connected = false;
            listener.onDisconnected("Connection lost");
            other.listener.onDisconnected("Connection lost");
        }

        @Override
        public boolean isListening() {
            return isServer && listening;
        }

        @Override
        public String localIp() {
            return "10.0.0.1";
        }

        Message lastSent() {
            return sent.get(sent.size() - 1);
        }
    }
}
