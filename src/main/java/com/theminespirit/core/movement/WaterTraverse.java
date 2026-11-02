package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;

public final class WaterTraverse {
    public enum Phase {
        ENTER,
        SWIM,
        EXIT
    }

    private WaterTraverse() {}

    public static Phase phase(boolean inWater, boolean targetWater) {
        if (!inWater && targetWater) {
            return Phase.ENTER;
        }
        if (inWater && !targetWater) {
            return Phase.EXIT;
        }
        return Phase.SWIM;
    }

    public static boolean arrived(double x, double y, double z, BlockCoord cell) {
        return Math.abs(x - (cell.x() + 0.5)) < 0.5
                && Math.abs(z - (cell.z() + 0.5)) < 0.5
                && Math.abs(y - cell.y()) < 1.0;
    }
}
