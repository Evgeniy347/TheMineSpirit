// lib: Из Baritone (github.com/cabaletta/baritone, src/api/java/baritone/api/pathing/goals/GoalGetToBlock.java),
// лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.Objects;

public final class NavGoalGetToBlock implements NavGoal {
    private final int x;
    private final int y;
    private final int z;
    private final double weight;

    public NavGoalGetToBlock(int x, int y, int z) {
        this(x, y, z, NavActionCosts.DEFAULT_HEURISTIC_WEIGHT);
    }

    public NavGoalGetToBlock(int x, int y, int z, double weight) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.weight = weight;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        int xDiff = x - this.x;
        int yDiff = y - this.y;
        int zDiff = z - this.z;
        return Math.abs(xDiff) + Math.abs(yDiff < 0 ? yDiff + 1 : yDiff) + Math.abs(zDiff) <= 1;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        int xDiff = x - this.x;
        int yDiff = y - this.y;
        int zDiff = z - this.z;
        return NavGoalBlock.calculate(xDiff, yDiff < 0 ? yDiff + 1 : yDiff, zDiff, weight);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NavGoalGetToBlock goal)) {
            return false;
        }
        return x == goal.x && y == goal.y && z == goal.z && Double.compare(weight, goal.weight) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z, weight);
    }

    @Override
    public String toString() {
        return "NavGoalGetToBlock{x=" + x + ",y=" + y + ",z=" + z + "}";
    }
}
