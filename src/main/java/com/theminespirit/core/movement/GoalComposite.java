package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public record GoalComposite(List<Goal> anyOf) implements Goal {
    public GoalComposite {
        Objects.requireNonNull(anyOf, "Список целей не может быть null: без него неизвестно, что считать достижением");
        anyOf = List.copyOf(anyOf);
        if (anyOf.isEmpty()) {
            throw new IllegalArgumentException("Список целей пуст: составная цель без вложенных недостижима, "
                    + "дух встанет до первого шага");
        }
    }

    @Override
    public boolean isSatisfied(BlockCoord feet) {
        Objects.requireNonNull(feet, "Позиция духа не может быть null: без неё неизвестно, дошёл ли дух");
        for (Goal goal : anyOf) {
            if (goal.isSatisfied(feet)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double heuristic(BlockCoord from) {
        Objects.requireNonNull(from, "Позиция духа не может быть null: без неё неизвестно расстояние до цели");
        double best = Double.POSITIVE_INFINITY;
        for (Goal goal : anyOf) {
            best = Math.min(best, goal.heuristic(from));
        }
        return best;
    }
}
