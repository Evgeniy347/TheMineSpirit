package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class WallEscapeRules {
    private WallEscapeRules() {}

    public static Optional<BlockCoord> nearestFree(
            BlockCoord feet, List<BlockCoord> candidates, Predicate<BlockCoord> usable) {
        Objects.requireNonNull(feet, "Клетка духа не может быть null: без неё непонятно, откуда бежать");
        Objects.requireNonNull(candidates, "Список клеток побега не может быть null: бежать некуда");
        Objects.requireNonNull(usable, "Признак свободной клетки не может быть null: без него выбор случаен");
        return candidates.stream()
                .filter(cell -> !cell.equals(feet))
                .filter(usable)
                .min(order(feet));
    }

    private static Comparator<BlockCoord> order(BlockCoord feet) {
        return (first, second) -> {
            int byDistance = Double.compare(distanceSqr(first, feet), distanceSqr(second, feet));
            if (byDistance != 0) {
                return byDistance;
            }
            if (first.x() != second.x()) {
                return Integer.compare(first.x(), second.x());
            }
            if (first.y() != second.y()) {
                return Integer.compare(first.y(), second.y());
            }
            return Integer.compare(first.z(), second.z());
        };
    }

    private static double distanceSqr(BlockCoord first, BlockCoord second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
