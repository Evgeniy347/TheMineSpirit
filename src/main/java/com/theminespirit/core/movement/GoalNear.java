package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record GoalNear(BlockCoord target, int radius) implements Goal {
    public GoalNear {
        Objects.requireNonNull(target, "Цель не может быть null: без клетки неизвестно, к чему подходить");
        if (radius < 0) {
            throw new IllegalArgumentException("Радиус окрестности " + radius
                    + " отрицателен: цель с отрицательным радиусом недостижима и дух встанет");
        }
    }

    @Override
    public boolean isSatisfied(BlockCoord feet) {
        Objects.requireNonNull(feet, "Позиция духа не может быть null: без неё неизвестно, дошёл ли дух");
        return Goal.octile(feet, target) <= radius;
    }

    @Override
    public double heuristic(BlockCoord from) {
        Objects.requireNonNull(from, "Позиция духа не может быть null: без неё неизвестно расстояние до цели");
        return Math.max(0.0, Goal.octile(from, target) - radius);
    }
}
