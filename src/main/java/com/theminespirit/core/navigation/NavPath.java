// lib: Из Baritone (github.com/cabaletta/baritone, src/main/java/baritone/pathing/calc/Path.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public record NavPath(List<BlockCoord> positions, double cost, boolean canReach) {
    public NavPath {
        positions = List.copyOf(Objects.requireNonNull(positions, "Позиции пути не могут быть null"));
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("Пустой путь нечего исполнять: пустой список — это провал поиска");
        }
        if (cost < 0) {
            throw new IllegalArgumentException("Стоимость пути " + cost + " отрицательна");
        }
    }

    public BlockCoord start() {
        return positions.get(0);
    }

    public BlockCoord end() {
        return positions.get(positions.size() - 1);
    }

    public int length() {
        return positions.size();
    }
}
