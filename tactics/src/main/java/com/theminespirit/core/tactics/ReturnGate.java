package com.theminespirit.core.tactics;

import com.theminespirit.core.engine.RunStatus;

public final class ReturnGate {
    private ReturnGate() {}

    public static boolean allowed(RunStatus status) {
        return allowed(status, false);
    }

    public static boolean allowed(RunStatus status, boolean hasScript) {
        return status != RunStatus.RUNNING && !hasScript;
    }
}
