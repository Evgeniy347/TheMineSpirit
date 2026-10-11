package com.theminespirit.core.movement;

public final class GotoTuning {
    public static final int TICKS_PER_BLOCK = 40;
    public static final int RESERVE_TICKS = 200;
    public static final int STUCK_TICKS = 40;
    public static final double STUCK_MIN_MOVEMENT = 0.05;

    private GotoTuning() {
    }

    public static int timeoutTicks(double distanceBlocks) {
        double estimate = Math.max(0, distanceBlocks) * TICKS_PER_BLOCK + RESERVE_TICKS;
        if (estimate >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(estimate);
    }
}
