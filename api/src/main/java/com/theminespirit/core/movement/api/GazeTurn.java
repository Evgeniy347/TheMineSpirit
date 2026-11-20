package com.theminespirit.core.movement.api;

public final class GazeTurn {
    public static final float IDLE_DEGREES_PER_TICK = 180.0F;
    public static final float WORK_DEGREES_PER_TICK = 30.0F;

    private GazeTurn() {}

    public static float forWork(boolean working) {
        return working ? WORK_DEGREES_PER_TICK : IDLE_DEGREES_PER_TICK;
    }
}
