// lib: Из Baritone (github.com/cabaletta/baritone, src/api/java/baritone/api/utils/PathCalculationResult.java),
// лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record NavResult(NavSearchStatus status, @Nullable NavPath path, int nodes, long millis, BlockCoord end) {
    public NavResult {
        Objects.requireNonNull(status, "Итог поиска не может быть null");
        Objects.requireNonNull(end, "Конечная клетка не может быть null: от неё продолжается поиск");
        if (nodes < 0 || millis < 0) {
            throw new IllegalArgumentException("Счётчики поиска (узлы " + nodes + ", мс " + millis + ") отрицательны");
        }
        if (status == NavSearchStatus.FAILURE != (path == null)) {
            throw new IllegalArgumentException("Провал без пути и найденный путь с итогом " + status + " несовместимы");
        }
    }

    public boolean found() {
        return status == NavSearchStatus.FOUND;
    }
}
