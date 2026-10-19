package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class TreeChooser {
    private TreeChooser() {}

    public static Optional<BlockCoord> next(
            int fromX, int fromY, int fromZ, List<BlockCoord> logs, Set<BlockCoord> known) {
        Objects.requireNonNull(logs, "logs не может быть null: из найденных брёвен выбирается следующее дерево");
        Objects.requireNonNull(
                known, "known не может быть null: разобранные и отвергнутые брёвна должны быть исключены");
        return logs.stream()
                .filter(coord -> !known.contains(coord))
                .min(Comparator.comparingLong((BlockCoord coord) -> distanceSquared(fromX, fromY, fromZ, coord))
                        .thenComparingInt(BlockCoord::y)
                        .thenComparingInt(BlockCoord::x)
                        .thenComparingInt(BlockCoord::z));
    }

    private static long distanceSquared(int x, int y, int z, BlockCoord coord) {
        long dx = (long) x - coord.x();
        long dy = (long) y - coord.y();
        long dz = (long) z - coord.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
