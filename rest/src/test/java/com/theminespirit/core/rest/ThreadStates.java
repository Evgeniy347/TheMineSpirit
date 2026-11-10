package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.fail;

final class ThreadStates {
    private ThreadStates() {}

    static void awaitState(Thread thread, Thread.State expected, String message) {
        while (true) {
            Thread.State state = thread.getState();
            if (state == expected) {
                return;
            }
            if (state == Thread.State.TERMINATED) {
                fail(message + "; поток завершился, так и не дойдя до состояния " + expected);
            }
            Thread.onSpinWait();
        }
    }
}
