package com.theminespirit.core.movement.api;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class RouteDescent {
    public static final int MAX_BELOW = 8;

    public record Height(int y, boolean wet) {}

    private RouteDescent() {}

    public static @Nullable String refusal(int startY, int targetY, List<Integer> heights) {
        Objects.requireNonNull(heights, "Высоты маршрута не могут быть null: без них не видно спуска");
        return refusalOf(
                startY, targetY, heights.stream().map(y -> new Height(y, false)).toList());
    }

    public static @Nullable String refusalOf(int startY, int targetY, List<Height> heights) {
        Objects.requireNonNull(heights, "Высоты маршрута не могут быть null: без них не видно спуска");
        if (heights.isEmpty()) {
            return null;
        }
        int floor = Math.min(startY, targetY) - MAX_BELOW;
        int lowest = Integer.MAX_VALUE;
        for (Height height : heights) {
            if (height.wet()) {
                continue;
            }
            lowest = Math.min(lowest, height.y());
        }
        if (lowest != Integer.MAX_VALUE && lowest < floor) {
            return "маршрут спускается до y=" + lowest + ": ниже цели и старта больше чем на " + MAX_BELOW
                    + " блоков (предел " + floor + ") — это яма или пещера без выхода";
        }
        return null;
    }
}
