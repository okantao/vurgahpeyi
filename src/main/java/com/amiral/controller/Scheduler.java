package com.amiral.controller;

/** Runs a task after a delay. Real code uses a background thread, tests run tasks by hand. */
@FunctionalInterface
public interface Scheduler {

    void schedule(Runnable task, long delayMillis);
}
