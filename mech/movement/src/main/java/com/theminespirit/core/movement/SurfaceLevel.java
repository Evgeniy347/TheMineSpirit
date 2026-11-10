package com.theminespirit.core.movement;

import java.util.List;
import java.util.Objects;

public final class SurfaceLevel {
    public static final int MARGIN = 2;

    private SurfaceLevel() {}

    public static boolean escaped(boolean skyOpen, int feetY, List<Integer> surroundingHeights, boolean walkExit) {
        Objects.requireNonNull(surroundingHeights, "Высоты вокруг не могут быть null: без них яму не отличить");
        if (walkExit) {
            return true;
        }
        if (!skyOpen) {
            return false;
        }
        if (surroundingHeights.isEmpty()) {
            return true;
        }
        int around = Integer.MIN_VALUE;
        for (int height : surroundingHeights) {
            around = Math.max(around, height);
        }
        return feetY + MARGIN >= around && feetY <= around + MARGIN;
    }
}
