// lib: Из Baritone (github.com/cabaletta/baritone, src/main/java/baritone/pathing/calc/PathNode.java), лицензия
// LGPL-3.0.
package com.theminespirit.core.navigation;

import org.jspecify.annotations.Nullable;

public final class NavNode {
    private static final long X_MASK = 0x3FFFFFFL;
    private static final long Z_MASK = 0x3FFFFFFL;
    private static final long Y_MASK = 0xFFFL;

    public final int x;
    public final int y;
    public final int z;
    public final double estimatedCostToGoal;

    double cost;
    double combinedCost;

    @Nullable
    NavNode previous;

    int heapPosition;

    public NavNode(int x, int y, int z, NavGoal goal) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.cost = NavActionCosts.COST_INF;
        this.estimatedCostToGoal = goal.heuristic(x, y, z);
        if (Double.isNaN(estimatedCostToGoal)) {
            throw new IllegalStateException("Цель вернула NaN для узла " + x + ", " + y + ", " + z);
        }
        this.heapPosition = -1;
    }

    public static long key(int x, int y, int z) {
        return (x & X_MASK) << 38 | (z & Z_MASK) << 12 | (y & Y_MASK);
    }

    public boolean isOpen() {
        return heapPosition != -1;
    }

    public double combinedCost() {
        return combinedCost;
    }

    public double cost() {
        return cost;
    }

    public int heapPosition() {
        return heapPosition;
    }

    public @Nullable NavNode previous() {
        return previous;
    }
}
