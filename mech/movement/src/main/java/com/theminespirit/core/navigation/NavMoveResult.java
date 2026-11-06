// lib: Из Baritone (github.com/cabaletta/baritone, utils/pathing/MutableMoveResult.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

final class NavMoveResult {
    int x;
    int y;
    int z;
    double cost;

    void reset(int startX, int startY, int startZ) {
        x = startX;
        y = startY;
        z = startZ;
        cost = NavActionCosts.COST_INF;
    }
}
