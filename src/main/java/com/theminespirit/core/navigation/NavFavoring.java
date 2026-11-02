// lib: Из Baritone (github.com/cabaletta/baritone, utils/pathing/Favoring.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.HashMap;
import java.util.Map;

public final class NavFavoring {
    public static final NavFavoring EMPTY = new NavFavoring(Map.of());

    private final Map<Long, Double> factors;

    public NavFavoring(Map<Long, Double> factors) {
        this.factors = new HashMap<>(factors);
        for (Map.Entry<Long, Double> entry : this.factors.entrySet()) {
            if (!(entry.getValue() > 0)) {
                throw new IllegalArgumentException("Множитель цены " + entry.getValue() + " для узла " + entry.getKey()
                        + ": ноль или минус ломают A*");
            }
        }
    }

    public static NavFavoring of(NavPath previous, double factor) {
        if (!(factor > 0)) {
            throw new IllegalArgumentException("Множитель прошлого пути " + factor + " не положителен");
        }
        Map<Long, Double> factors = new HashMap<>();
        for (var cell : previous.positions()) {
            factors.put(NavNode.key(cell.x(), cell.y(), cell.z()), factor);
        }
        return new NavFavoring(factors);
    }

    public double factor(int x, int y, int z) {
        return factor(NavNode.key(x, y, z));
    }

    public double factor(long key) {
        return factors.getOrDefault(key, 1.0);
    }

    public boolean isEmpty() {
        return factors.isEmpty();
    }
}
