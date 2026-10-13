package com.theminespirit.core.building;

import com.theminespirit.core.movement.GotoTuning;

public final class BuildTuning {
    public static final double STAND_ARRIVAL = 0.6;
    public static final int MAX_CONSECUTIVE_SKIPS = 16;

    private BuildTuning() {
    }

    public static int timeoutTicks(double distanceBlocks) {
        return GotoTuning.timeoutTicks(distanceBlocks);
    }
}
