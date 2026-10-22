package com.amiral.controller;

import com.amiral.net.Message;
import com.amiral.net.NetworkListener;
import java.io.IOException;

/** What the controller needs from the network. Real sockets in {@link SocketGateway}, fakes in tests. */
public interface NetworkGateway {

    /** Binds the port and starts accepting a client. */
    void startServer(int port, String nickname, NetworkListener listener) throws IOException;

    /** Connects and completes the handshake (blocking, max 5 s). */
    void connect(String host, int port, String nickname, NetworkListener listener) throws IOException;

    boolean send(Message m);

    /** Server: closes the client and the server socket. Client: sends QUIT and closes. */
    void close();

    /** Server only: drops the current client but keeps listening. */
    void dropClient();

    boolean isListening();

    /** Local IPv4 address shown in "My IP Address". */
    String localIp();
}
