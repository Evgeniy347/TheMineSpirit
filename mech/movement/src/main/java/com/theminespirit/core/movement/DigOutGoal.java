package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public final class DigOutGoal {
    public static final int HOME_RADIUS = 2;

    private DigOutGoal() {}

    public static boolean ascent(BlockCoord feet, BlockCoord target, int radius) {
        Objects.requireNonNull(feet, "Клетка духа не может быть null: по ней видно, вышел ли он");
        Objects.requireNonNull(target, "Цель выхода не может быть null: без неё выход не закончить");
        if (radius < 0) {
            throw new IllegalArgumentException("Радиус дома " + radius + " отрицателен: такую цель не задать");
        }
        return feet.y() >= target.y()
                && Math.abs(feet.x() - target.x()) <= radius
                && Math.abs(feet.z() - target.z()) <= radius;
    }
}
