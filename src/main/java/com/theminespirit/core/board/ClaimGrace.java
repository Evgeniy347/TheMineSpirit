package com.theminespirit.core.board;

public final class ClaimGrace {
    public static final long TICKS = 200;

    private ClaimGrace() {}

    public static boolean expired(long since, long now) {
        return now - since >= TICKS;
    }
}
