package com.amiral.util;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Central java.util.logging setup. Use instead of System.out.println. */
public final class AppLogger {

    private static final String ROOT = "com.amiral";
    private static volatile boolean configured;

    private AppLogger() {
    }

    public static Logger get(Class<?> type) {
        configure();
        return Logger.getLogger(type.getName());
    }

    private static synchronized void configure() {
        if (configured) {
            return;
        }
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT %4$s %3$s - %5$s%6$s%n");
        Logger root = Logger.getLogger(ROOT);
        root.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        handler.setLevel(Level.ALL);
        root.addHandler(handler);
        root.setLevel(Level.INFO);
        configured = true;
    }
}
