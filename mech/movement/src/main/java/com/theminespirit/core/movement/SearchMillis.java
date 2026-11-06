package com.theminespirit.core.movement;

public final class SearchMillis {
    private SearchMillis() {}

    public static long since(long startedNanos, long nowNanos) {
        if (startedNanos <= 0 || nowNanos < startedNanos) {
            return 0;
        }
        return (nowNanos - startedNanos) / 1_000_000L;
    }
}
