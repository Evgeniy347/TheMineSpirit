// lib: Из Baritone (github.com/cabaletta/baritone, api/pathing/goals/Goal.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import org.jspecify.annotations.Nullable;

public interface NavGoal {
    boolean isInGoal(int x, int y, int z);

    double heuristic(int x, int y, int z);

    default double heuristic() {
        return 0;
    }

    default @Nullable Integer goalY() {
        return null;
    }
}
