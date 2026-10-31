package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.TimeUnit;

final class ThreadStates {
    private ThreadStates() {}

    static void awaitState(Thread thread, Thread.State expected, String message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != expected) {
            if (System.nanoTime() > deadline) {
                fail(message + "; состояние потока: " + thread.getState());
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("Ожидание состояния потока прервано: " + message);
            }
        }
    }

    static void awaitWaiting(Thread thread, String message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING) {
            if (System.nanoTime() > deadline) {
                fail(message + "; состояние потока: " + thread.getState());
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("Ожидание состояния потока прервано: " + message);
            }
        }
    }
}
