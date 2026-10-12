package com.theminespirit.core.rest;

public interface SseStream extends AutoCloseable {
    String nextFrame() throws InterruptedException;

    @Override
    void close();
}
