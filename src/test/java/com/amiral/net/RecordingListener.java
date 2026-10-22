package com.amiral.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Test listener that queues every callback so tests can wait for them. */
class RecordingListener implements NetworkListener {

    final BlockingQueue<String> connected = new LinkedBlockingQueue<>();
    final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();
    final BlockingQueue<String> disconnected = new LinkedBlockingQueue<>();
    final BlockingQueue<Exception> errors = new LinkedBlockingQueue<>();

    @Override
    public void onConnected(String remoteNickname, String remoteAddress) {
        connected.add(remoteNickname);
    }

    @Override
    public void onMessage(Message m) {
        messages.add(m);
    }

    @Override
    public void onDisconnected(String reason) {
        disconnected.add(reason);
    }

    @Override
    public void onError(Exception e) {
        errors.add(e);
    }

    static <T> T await(BlockingQueue<T> q) throws InterruptedException {
        T value = q.poll(5, TimeUnit.SECONDS);
        assertNotNull(value, "timed out waiting");
        return value;
    }

    Message next(MessageType expected) throws InterruptedException {
        Message m = await(messages);
        assertEquals(expected, m.type());
        return m;
    }
}
