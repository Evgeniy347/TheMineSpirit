package com.theminespirit.core.movement;

import java.util.List;
import java.util.Objects;

public final class SurfaceLevel {
    public static final int MARGIN = 2;

    private SurfaceLevel() {}

    public static boolean escaped(boolean skyOpen, int feetY, List<Integer> surroundingHeights) {
        Objects.requireNonNull(surroundingHeights, "Высоты вокруг не могут быть null: без них яму не отличить");
        if (!skyOpen) {
            return false;
        }
        int highest = feetY;
        for (int height : surroundingHeights) {
            highest = Math.max(highest, height);
        }
        return feetY + MARGIN >= highest;
    }
}
