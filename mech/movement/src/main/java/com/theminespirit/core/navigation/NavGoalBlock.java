// lib: Из Baritone (github.com/cabaletta/baritone, api/pathing/goals/GoalBlock.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.Objects;

public final class NavGoalBlock implements NavGoal {
    private final int x;
    private final int y;
    private final int z;
    private final double weight;

    public NavGoalBlock(int x, int y, int z) {
        this(x, y, z, NavActionCosts.DEFAULT_HEURISTIC_WEIGHT);
    }

    public NavGoalBlock(int x, int y, int z, double weight) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.weight = weight;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return x == this.x && y == this.y && z == this.z;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        return calculate(x - this.x, y - this.y, z - this.z, weight);
    }

    public static double calculate(double xDiff, int yDiff, double zDiff, double weight) {
        return NavGoalYLevel.calculate(0, yDiff) + NavGoalXZ.calculate(xDiff, zDiff, weight);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NavGoalBlock goal)) {
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
        return "NavGoalBlock{x=" + x + ",y=" + y + ",z=" + z + "}";
    }
}
