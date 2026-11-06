// lib: Из Baritone (github.com/cabaletta/baritone, api/pathing/goals/GoalNear.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.Objects;

public final class NavGoalNear implements NavGoal {
    private final int x;
    private final int y;
    private final int z;
    private final int rangeSq;
    private final double weight;

    public NavGoalNear(int x, int y, int z, int range) {
        this(x, y, z, range, NavActionCosts.DEFAULT_HEURISTIC_WEIGHT);
    }

    public NavGoalNear(int x, int y, int z, int range, double weight) {
        if (range < 0) {
            throw new IllegalArgumentException("Радиус цели " + range + " отрицателен: цель не станет ближе");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.rangeSq = range * range;
        this.weight = weight;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        int xDiff = x - this.x;
        int yDiff = y - this.y;
        int zDiff = z - this.z;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff <= rangeSq;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        return NavGoalBlock.calculate(x - this.x, y - this.y, z - this.z, weight);
    }

    @Override
    public Integer goalY() {
        return y;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NavGoalNear goal)) {
            return false;
        }
        return x == goal.x
                && y == goal.y
                && z == goal.z
                && rangeSq == goal.rangeSq
                && Double.compare(weight, goal.weight) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z, rangeSq, weight);
    }

    @Override
    public String toString() {
        return "NavGoalNear{x=" + x + ",y=" + y + ",z=" + z + ",rangeSq=" + rangeSq + "}";
    }
}
