package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class MineVeinStands {
    private MineVeinStands() {}

    public static Optional<BlockCoord> next(
            List<BlockCoord> targets,
            BlockCoord feet,
            SafetyView view,
            Set<BlockCoord> excluded,
            Set<BlockCoord> tried) {
        Objects.requireNonNull(targets, "Цели не могут быть null: без них неизвестно, к чему подходить");
        Objects.requireNonNull(feet, "Точка духа не может быть null: по ней считается ближайшая стоянка");
        Objects.requireNonNull(view, "Вид мира не может быть null: по нему проверяется стоянка");
        Objects.requireNonNull(excluded, "Исключённые стоянки не могут быть null: пустое множество задаётся пустым");
        Objects.requireNonNull(tried, "Испробованные стоянки не могут быть null: пустое множество задаётся пустым");
        List<BlockCoord> ordered = new ArrayList<>(targets);
        ordered.sort(Comparator.comparingLong((BlockCoord target) -> distanceSquared(feet, target))
                .thenComparing(Comparator.naturalOrder()));
        BlockCoord best = null;
        long bestDistance = Long.MAX_VALUE;
        for (BlockCoord target : ordered) {
            for (BlockCoord stand : StandSpots.of(target, view, feet)) {
                if (excluded.contains(stand) || tried.contains(stand) || MineCellStands.occupies(stand, target)) {
                    continue;
                }
                long distance = distanceSquared(feet, stand);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = stand;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
