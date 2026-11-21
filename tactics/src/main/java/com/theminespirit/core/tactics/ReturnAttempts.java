package com.theminespirit.core.tactics;

public final class ReturnAttempts {
    public static final int LIMIT = 2;

    private ReturnAttempts() {}

    public static boolean keep(int failed) {
        return failed < LIMIT;
    }
}
