package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;

public sealed interface Goal permits GoalBlock, GoalXZ, GoalNear, GoalComposite {
    double DIAGONAL_EXTRA = Math.sqrt(2.0) - 1.0;

    boolean isSatisfied(BlockCoord feet);

    double heuristic(BlockCoord from);

    static double octile(BlockCoord from, BlockCoord target) {
        int dx = Math.abs(from.x() - target.x());
        int dy = Math.abs(from.y() - target.y());
        int dz = Math.abs(from.z() - target.z());
        return Math.max(dx, dz) + DIAGONAL_EXTRA * Math.min(dx, dz) + dy;
    }
}
