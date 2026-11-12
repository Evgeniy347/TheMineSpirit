package com.theminespirit.core.board.lease;

public final class LeaseUndoLimit {
    private LeaseUndoLimit() {}

    public static boolean exhausted(int failures, int maxFailures) {
        return failures >= maxFailures;
    }
}
