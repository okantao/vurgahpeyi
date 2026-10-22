package com.amiral.controller;

import com.amiral.net.GameClient;
import com.amiral.net.GameServer;
import com.amiral.net.Message;
import com.amiral.net.NetUtil;
import com.amiral.net.NetworkListener;
import java.io.IOException;

/** {@link NetworkGateway} over real TCP sockets. */
public class SocketGateway implements NetworkGateway {

    private GameServer server;
    private GameClient client;

    @Override
    public synchronized void startServer(int port, String nickname, NetworkListener listener) throws IOException {
        GameServer s = new GameServer(listener, nickname);
        s.start(port);
        server = s;
        client = null;
    }

    @Override
    public void connect(String host, int port, String nickname, NetworkListener listener) throws IOException {
        GameClient c = new GameClient(listener);
        synchronized (this) {
            client = c;
            server = null;
        }
        c.connect(host, port, nickname);
    }

    @Override
    public synchronized boolean send(Message m) {
        if (server != null) {
            return server.send(m);
        }
        return client != null && client.send(m);
    }

    @Override
    public void close() {
        GameServer s;
        GameClient c;
        synchronized (this) {
            s = server;
            c = client;
            server = null;
            client = null;
        }
        if (s != null) {
            s.send(Message.quit());
            s.stop();
        }
        if (c != null) {
            c.disconnect();
        }
    }

    @Override
    public void dropClient() {
        GameServer s;
        synchronized (this) {
            s = server;
        }
        if (s != null) {
            s.send(Message.quit());
            s.dropClient("Dropped by server");
        }
    }

    @Override
    public synchronized boolean isListening() {
        return server != null && server.isListening();
    }

    @Override
    public String localIp() {
        return NetUtil.localIPv4();
    }
}
