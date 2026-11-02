// lib: Из Baritone (github.com/cabaletta/baritone, pathing/calc/Path.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public record NavSegmentedRoute(
        NavSearchStatus status, List<BlockCoord> positions, int segments, int nodes, long millis, BlockCoord end) {
    public NavSegmentedRoute {
        Objects.requireNonNull(status, "Итог сегментов не может быть null");
        positions = List.copyOf(Objects.requireNonNull(positions, "Позиции сегментов не могут быть null"));
        Objects.requireNonNull(end, "Конец сегментов не может быть null");
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("Сегментированный путь без позиций: старт обязан быть в списке");
        }
        if (segments < 0 || nodes < 0 || millis < 0) {
            throw new IllegalArgumentException("Счётчики сегментов (сегментов " + segments + ", узлов " + nodes
                    + ", мс " + millis + ") отрицательны");
        }
    }

    public boolean found() {
        return status == NavSearchStatus.FOUND;
    }
}
