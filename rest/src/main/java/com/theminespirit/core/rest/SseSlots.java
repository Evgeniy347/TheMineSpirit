package com.theminespirit.core.rest;

public final class SseSlots {
    public static final int MAX_STREAMS = 4;

    private int open;

    public synchronized boolean tryAcquire() {
        if (open >= MAX_STREAMS) {
            return false;
        }
        open++;
        return true;
    }

    public synchronized void release() {
        if (open > 0) {
            open--;
        }
    }

    public synchronized int open() {
        return open;
    }
}
