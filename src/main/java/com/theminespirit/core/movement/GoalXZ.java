package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record GoalXZ(int x, int z) implements Goal {
    @Override
    public boolean isSatisfied(BlockCoord feet) {
        Objects.requireNonNull(feet, "Позиция духа не может быть null: без неё неизвестно, дошёл ли дух");
        return feet.x() == x && feet.z() == z;
    }

    @Override
    public double heuristic(BlockCoord from) {
        Objects.requireNonNull(from, "Позиция духа не может быть null: без неё неизвестно расстояние до цели");
        int dx = Math.abs(from.x() - x);
        int dz = Math.abs(from.z() - z);
        return Math.max(dx, dz) + Goal.DIAGONAL_EXTRA * Math.min(dx, dz);
    }
}
