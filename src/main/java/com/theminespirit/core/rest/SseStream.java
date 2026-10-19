package com.theminespirit.core.rest;

import org.jspecify.annotations.Nullable;

public interface SseStream extends AutoCloseable {
    @Nullable
    String nextFrame() throws InterruptedException;

    @Override
    void close();
}
