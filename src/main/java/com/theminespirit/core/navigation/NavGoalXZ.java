// lib: Из Baritone (github.com/cabaletta/baritone, src/api/java/baritone/api/pathing/goals/GoalXZ.java), лицензия
// LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.Objects;

public final class NavGoalXZ implements NavGoal {
    private static final double SQRT_2 = Math.sqrt(2);

    private final int x;
    private final int z;
    private final double weight;

    public NavGoalXZ(int x, int z) {
        this(x, z, NavActionCosts.DEFAULT_HEURISTIC_WEIGHT);
    }

    public NavGoalXZ(int x, int z, double weight) {
        this.x = x;
        this.z = z;
        this.weight = weight;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return x == this.x && z == this.z;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        return calculate(x - this.x, z - this.z, weight);
    }

    public static double calculate(double xDiff, double zDiff, double weight) {
        double x = Math.abs(xDiff);
        double z = Math.abs(zDiff);
        double straight = Math.max(x, z) - Math.min(x, z);
        double diagonal = Math.min(x, z) * SQRT_2;
        return (diagonal + straight) * weight;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NavGoalXZ goal)) {
            return false;
        }
        return x == goal.x && z == goal.z && Double.compare(weight, goal.weight) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z, weight);
    }

    @Override
    public String toString() {
        return "NavGoalXZ{x=" + x + ",z=" + z + "}";
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }
}
