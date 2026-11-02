// lib: Из Baritone (github.com/cabaletta/baritone, src/api/java/baritone/api/pathing/goals/GoalComposite.java),
// лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.List;
import java.util.Objects;

public final class NavGoalComposite implements NavGoal {
    private final List<NavGoal> goals;

    public NavGoalComposite(List<NavGoal> goals) {
        this.goals = List.copyOf(Objects.requireNonNull(goals, "Список целей не может быть null"));
        if (this.goals.isEmpty()) {
            throw new IllegalArgumentException("Составная цель без вложенных недостижима: дух встанет");
        }
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        for (NavGoal goal : goals) {
            if (goal.isInGoal(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double best = Double.POSITIVE_INFINITY;
        for (NavGoal goal : goals) {
            best = Math.min(best, goal.heuristic(x, y, z));
        }
        return best;
    }

    @Override
    public double heuristic() {
        double best = Double.POSITIVE_INFINITY;
        for (NavGoal goal : goals) {
            best = Math.min(best, goal.heuristic());
        }
        return best;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NavGoalComposite composite && composite.goals.equals(goals);
    }

    @Override
    public int hashCode() {
        return goals.hashCode();
    }

    @Override
    public String toString() {
        return "NavGoalComposite" + goals;
    }
}
