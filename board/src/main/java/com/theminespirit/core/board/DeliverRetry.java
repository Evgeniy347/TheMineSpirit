package com.theminespirit.core.board;

public final class DeliverRetry {
    public static final int MAX_RESTARTS = 2;

    private DeliverRetry() {}

    public static boolean canRetry(int restarts) {
        return restarts >= 0 && restarts < MAX_RESTARTS;
    }
}
