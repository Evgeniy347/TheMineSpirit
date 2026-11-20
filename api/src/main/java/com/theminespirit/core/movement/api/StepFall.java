package com.theminespirit.core.movement.api;

public final class StepFall {
    private StepFall() {}

    public static boolean fell(int feetY, int targetY, boolean onGround) {
        return !onGround && feetY < targetY - 1;
    }
}
