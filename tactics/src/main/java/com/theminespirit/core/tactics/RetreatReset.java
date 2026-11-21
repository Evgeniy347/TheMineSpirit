package com.theminespirit.core.tactics;

public final class RetreatReset {
    private RetreatReset() {}

    public static boolean needed(boolean hasCurrent, boolean hasTarget) {
        return hasCurrent || hasTarget;
    }
}
