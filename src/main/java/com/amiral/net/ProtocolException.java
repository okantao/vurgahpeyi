package com.amiral.net;

/** A line that does not follow the protocol. */
public class ProtocolException extends Exception {

    private static final long serialVersionUID = 1L;

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
