// lib: Из Baritone (github.com/cabaletta/baritone, pathing/calc/AStarPathFinder.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.function.LongSupplier;

public final class NavAStarSearch {
    private NavAStarSearch() {}

    public static NavResult plan(NavContext context, int startX, int startY, int startZ, NavGoal goal) {
        return plan(context, startX, startY, startZ, goal, NavFavoring.EMPTY, System::nanoTime);
    }

    public static NavResult plan(
            NavContext context, int startX, int startY, int startZ, NavGoal goal, NavFavoring favoring) {
        return plan(context, startX, startY, startZ, goal, favoring, System::nanoTime);
    }

    static NavResult plan(NavContext context, int startX, int startY, int startZ, NavGoal goal, LongSupplier clock) {
        return plan(context, startX, startY, startZ, goal, NavFavoring.EMPTY, clock);
    }

    static NavResult plan(
            NavContext context,
            int startX,
            int startY,
            int startZ,
            NavGoal goal,
            NavFavoring favoring,
            LongSupplier clock) {
        NavAStarSession session = new NavAStarSession(context, startX, startY, startZ, goal, favoring, clock);
        NavResult result;
        do {
            result = session.step(Long.MAX_VALUE);
        } while (result == null);
        return result;
    }
}
