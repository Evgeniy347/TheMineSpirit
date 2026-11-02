// lib: Из Baritone (github.com/cabaletta/baritone, src/api/java/baritone/api/pathing/movement/ActionCosts.java),
// лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

public interface NavActionCosts {
    double WALK_ONE_BLOCK_COST = 20 / 4.317;
    double WALK_ONE_IN_WATER_COST = 20 / 2.2;
    double LADDER_UP_ONE_COST = 20 / 2.35;
    double LADDER_DOWN_ONE_COST = 20 / 3.0;
    double WALK_OFF_BLOCK_COST = WALK_ONE_BLOCK_COST * 0.8;
    double CENTER_AFTER_FALL_COST = WALK_ONE_BLOCK_COST - WALK_OFF_BLOCK_COST;
    double COST_INF = 1000000;
    double DEFAULT_HEURISTIC_WEIGHT = 3.563;

    double[] FALL_N_BLOCKS_COST = generateFallNBlocksCost();

    double FALL_1_25_BLOCKS_COST = distanceToTicks(1.25);
    double FALL_0_25_BLOCKS_COST = distanceToTicks(0.25);
    double JUMP_ONE_BLOCK_COST = FALL_1_25_BLOCKS_COST - FALL_0_25_BLOCKS_COST;

    static double[] generateFallNBlocksCost() {
        double[] costs = new double[4097];
        for (int i = 0; i < 4097; i++) {
            costs[i] = distanceToTicks(i);
        }
        return costs;
    }

    static double velocity(int ticks) {
        return (Math.pow(0.98, ticks) - 1) * -3.92;
    }

    static double distanceToTicks(double distance) {
        if (distance == 0) {
            return 0;
        }
        double left = distance;
        int tickCount = 0;
        while (true) {
            double fallDistance = velocity(tickCount);
            if (left <= fallDistance) {
                return tickCount + left / fallDistance;
            }
            left -= fallDistance;
            tickCount++;
        }
    }
}
