package com.theminespirit.core.movement;

import java.util.List;
import java.util.Objects;

public final class PerchLevel {
    public static final int SAFE_DROP = 3;

    private PerchLevel() {}

    public static boolean perched(boolean skyOpen, int feetY, List<Integer> surroundingHeights) {
        Objects.requireNonNull(
                surroundingHeights, "Высоты вокруг не могут быть null: без них вершину столба не отличить");
        if (!skyOpen || surroundingHeights.isEmpty()) {
            return false;
        }
        int below = 0;
        for (int height : surroundingHeights) {
            if (height < feetY - SAFE_DROP) {
                below++;
            }
        }
        return below * 4 > surroundingHeights.size() * 3;
    }

    public static boolean escaped(boolean skyOpen, int feetY, List<Integer> surroundingHeights, boolean walkExit) {
        return !perched(skyOpen, feetY, surroundingHeights)
                && SurfaceLevel.escaped(skyOpen, feetY, surroundingHeights, walkExit);
    }
}
