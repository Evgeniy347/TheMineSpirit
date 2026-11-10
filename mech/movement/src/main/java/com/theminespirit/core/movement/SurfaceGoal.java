package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public final class SurfaceGoal {
    private SurfaceGoal() {}

    public static boolean home(BlockCoord cell, BlockCoord target, int radius) {
        Objects.requireNonNull(cell, "Клетка маршрута не может быть null: её проверяют на цель");
        Objects.requireNonNull(target, "Цель выхода не может быть null: без неё цель — любое небо");
        if (radius < 0) {
            throw new IllegalArgumentException("Радиус цели " + radius + " отрицателен: такой радиус не задать");
        }
        return Math.abs(cell.x() - target.x()) <= radius
                && Math.abs(cell.z() - target.z()) <= radius
                && Math.abs(cell.y() - target.y()) <= radius;
    }
}
