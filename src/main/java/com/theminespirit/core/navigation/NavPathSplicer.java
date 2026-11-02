// lib: Из Baritone (github.com/cabaletta/baritone, pathing/path/SplicedPath.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class NavPathSplicer {
    private NavPathSplicer() {}

    public static Optional<NavPath> splice(NavPath first, NavPath second, boolean allowOverlap) {
        Objects.requireNonNull(first, "Первый путь не может быть null");
        Objects.requireNonNull(second, "Второй путь не может быть null");
        if (!first.end().equals(second.start())) {
            return Optional.empty();
        }
        Set<BlockCoord> secondCells = new HashSet<>(second.positions());
        int firstIndex = -1;
        for (int i = 0; i < first.length() - 1; i++) {
            if (secondCells.contains(first.positions().get(i))) {
                firstIndex = i;
                break;
            }
        }
        if (firstIndex != -1 && !allowOverlap) {
            return Optional.empty();
        }
        if (firstIndex == -1) {
            firstIndex = first.length() - 1;
        }
        int secondIndex = second.positions().indexOf(first.positions().get(firstIndex));
        if (!allowOverlap && secondIndex != 0) {
            return Optional.empty();
        }
        List<BlockCoord> positions = new ArrayList<>();
        positions.addAll(first.positions().subList(0, firstIndex + 1));
        positions.addAll(second.positions().subList(secondIndex + 1, second.length()));
        return Optional.of(new NavPath(positions, first.cost() + second.cost(), second.canReach()));
    }
}
