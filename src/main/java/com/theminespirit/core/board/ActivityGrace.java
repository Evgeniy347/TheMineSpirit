package com.theminespirit.core.board;

public final class ActivityGrace {
    public static final long TICKS = 240;

    private ActivityGrace() {}

    public static boolean keep(long idleSince, long now) {
        return idleSince > 0 && now >= idleSince && now - idleSince < TICKS;
    }
}
