package com.theminespirit.core.movement;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class RouteDescent {
    public static final int MAX_BELOW = 8;

    private RouteDescent() {}

    public static @Nullable String refusal(int startY, int targetY, List<Integer> heights) {
        Objects.requireNonNull(heights, "Высоты маршрута не могут быть null: без них не видно спуска");
        if (heights.isEmpty()) {
            return null;
        }
        int floor = Math.min(startY, targetY) - MAX_BELOW;
        int lowest = Integer.MAX_VALUE;
        for (int height : heights) {
            lowest = Math.min(lowest, height);
        }
        if (lowest < floor) {
            return "маршрут спускается до y=" + lowest + ": ниже цели и старта больше чем на " + MAX_BELOW
                    + " блоков (предел " + floor + ") — это яма или пещера без выхода";
        }
        return null;
    }
}
