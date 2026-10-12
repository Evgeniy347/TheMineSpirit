package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record GoalBlock(BlockCoord target) implements Goal {
    public GoalBlock {
        Objects.requireNonNull(target, "Цель не может быть null: без клетки неизвестно, куда идти");
    }

    @Override
    public boolean isSatisfied(BlockCoord feet) {
        Objects.requireNonNull(feet, "Позиция духа не может быть null: без неё неизвестно, дошёл ли дух");
        return feet.equals(target);
    }

    @Override
    public double heuristic(BlockCoord from) {
        Objects.requireNonNull(from, "Позиция духа не может быть null: без неё неизвестно расстояние до цели");
        return Goal.octile(from, target);
    }
}
