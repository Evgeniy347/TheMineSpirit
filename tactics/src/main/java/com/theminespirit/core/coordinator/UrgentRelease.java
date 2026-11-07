package com.theminespirit.core.coordinator;

public final class UrgentRelease {
    private UrgentRelease() {}

    public static boolean holdInsteadOfReset(boolean busy) {
        return busy;
    }
}
