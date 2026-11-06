// lib: Из Baritone (github.com/cabaletta/baritone, api/pathing/goals/Goal.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

public interface NavGoal {
    boolean isInGoal(int x, int y, int z);

    double heuristic(int x, int y, int z);

    default double heuristic() {
        return 0;
    }
}
