package com.theminespirit.core.movement;

import java.util.OptionalInt;

final class PitWalkStep {
    private PitWalkStep() {}

    static OptionalInt next(PitWalkOut.World world, int x, int z, int y, int safeDrop) {
        OptionalInt low = world.groundY(x, z, y - safeDrop);
        if (low.isPresent() && low.getAsInt() < y && columnClear(world, x, z, low.getAsInt(), y + 1)) {
            return low;
        }
        return world.groundY(x, z, y);
    }

    private static boolean columnClear(PitWalkOut.World world, int x, int z, int from, int to) {
        for (int y = from; y <= to; y++) {
            if (!world.clear(x, y, z)) {
                return false;
            }
        }
        return true;
    }
}
