package com.theminespirit.core.tactics;

public final class AnchorReturn {
    private AnchorReturn() {}

    public static boolean allowed(boolean taskRunning) {
        return !taskRunning;
    }
}
