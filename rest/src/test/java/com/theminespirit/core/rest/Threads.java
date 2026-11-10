package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;

final class Threads {
    private Threads() {}

    static void await(CountDownLatch latch, String message) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("Ожидание события прервано: " + message);
        }
    }

    static void join(Thread thread, String message) {
        try {
            thread.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("Ожидание завершения потока прервано: " + message);
        }
    }
}
