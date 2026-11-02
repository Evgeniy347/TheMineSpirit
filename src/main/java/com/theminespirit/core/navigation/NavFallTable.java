package com.theminespirit.core.navigation;

final class NavFallTable {
    static final double[] COSTS = NavActionCosts.generateFallNBlocksCost();

    private NavFallTable() {}

    static int size() {
        return COSTS.length;
    }
}
