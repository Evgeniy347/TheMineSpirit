package com.theminespirit.core.engine;

public final class RunHalt {
    private RunHalt() {}

    public static boolean needed(RunStatus status) {
        return status != RunStatus.RUNNING;
    }
}
