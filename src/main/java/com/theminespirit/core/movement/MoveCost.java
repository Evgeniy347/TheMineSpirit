package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;

public final class MoveCost {
    public static final int WALK_TICKS = 5;

    private MoveCost() {}

    public static int walkTicks(BlockCoord from, BlockCoord to) {
        return (int) Math.ceil(WALK_TICKS * Goal.octile(from, to));
    }
}
