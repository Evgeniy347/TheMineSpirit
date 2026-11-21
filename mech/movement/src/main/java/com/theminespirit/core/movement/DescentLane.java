package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;

public final class DescentLane {
    private DescentLane() {}

    public interface Coverage {
        boolean solid(BlockCoord cell);
    }

    public static boolean blocked(BlockCoord feet, BlockCoord cell, Coverage coverage) {
        if (coverage.solid(cell)) {
            return true;
        }
        int dx = Integer.signum(cell.x() - feet.x());
        int dz = Integer.signum(cell.z() - feet.z());
        int distance = Math.max(Math.abs(cell.x() - feet.x()), Math.abs(cell.z() - feet.z()));
        int level = cell.y() + 1;
        for (int i = 1; i <= distance; i++) {
            if (coverage.solid(new BlockCoord(feet.x() + dx * i, level, feet.z() + dz * i))) {
                return true;
            }
        }
        return false;
    }
}
