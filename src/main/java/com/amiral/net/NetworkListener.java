package com.amiral.net;

/** Callbacks from the network layer. Called on network threads, never on the Swing EDT. */
public interface NetworkListener {

    /** The handshake finished; {@code remoteNickname} is the other player's name. */
    void onConnected(String remoteNickname, String remoteAddress);

    /** A protocol message other than the handshake and heartbeat. */
    void onMessage(Message m);

    /** The connection ended (closed by either side, timeout or stop). Called once per connection. */
    void onDisconnected(String reason);

    /** A non-fatal problem, e.g. a malformed line. */
    void onError(Exception e);
}
