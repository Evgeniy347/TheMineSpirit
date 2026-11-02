package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class MagnetSearch {
    public record Result(Optional<BlockCoord> target, int checked, List<BlockCoord> first) {}

    private MagnetSearch() {}

    public static Result around(BlockCoord goal, Predicate<BlockCoord> standable, int radius, int vertical) {
        Objects.requireNonNull(goal, "Цель магнита не может быть null: без неё некуда возвращать духа");
        Objects.requireNonNull(standable, "Проверка стоянки не может быть null: без неё магнит не отличит пол");
        if (radius < 0 || vertical < 0) {
            throw new IllegalArgumentException(
                    "Радиус магнита " + radius + " и высота " + vertical + " не могут быть отрицательными");
        }
        List<BlockCoord> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -vertical; dy <= vertical; dy++) {
                    candidates.add(new BlockCoord(goal.x() + dx, goal.y() + dy, goal.z() + dz));
                }
            }
        }
        candidates.sort(Comparator.comparingLong((BlockCoord cell) -> distanceSquared(goal, cell))
                .thenComparing(Comparator.naturalOrder()));
        List<BlockCoord> first = new ArrayList<>();
        int checked = 0;
        for (BlockCoord cell : candidates) {
            checked++;
            if (first.size() < 3) {
                first.add(cell);
            }
            if (standable.test(cell)) {
                return new Result(Optional.of(cell), checked, List.copyOf(first));
            }
        }
        return new Result(Optional.empty(), checked, List.copyOf(first));
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) from.x() - to.x();
        long dy = (long) from.y() - to.y();
        long dz = (long) from.z() - to.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
