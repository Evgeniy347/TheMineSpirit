// lib: Из Baritone (github.com/cabaletta/baritone, api/pathing/goals/GoalYLevel.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

public final class NavGoalYLevel implements NavGoal {
    private final int level;

    public NavGoalYLevel(int level) {
        this.level = level;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return y == level;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        return calculate(level, y);
    }

    @Override
    public Integer goalY() {
        return level;
    }

    public static double calculate(int goalY, int currentY) {
        if (currentY > goalY) {
            return NavActionCosts.fallNBlocksCost(2) / 2 * (currentY - goalY);
        }
        if (currentY < goalY) {
            return (goalY - currentY) * NavActionCosts.JUMP_ONE_BLOCK_COST;
        }
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NavGoalYLevel goal && goal.level == level;
    }

    @Override
    public int hashCode() {
        return level;
    }

    @Override
    public String toString() {
        return "NavGoalYLevel{level=" + level + "}";
    }
}
