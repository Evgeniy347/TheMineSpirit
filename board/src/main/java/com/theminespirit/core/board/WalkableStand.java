package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class WalkableStand {
    private static final int REACH_SQUARED = 20;

    private WalkableStand() {}

    public static Optional<BlockCoord> near(BlockCoord point, Set<BlockCoord> walkable) {
        Objects.requireNonNull(point, "Точка работы не может быть null: стоянку ищут рядом с ней");
        Objects.requireNonNull(walkable, "Проходимые клетки не могут быть null: стоянка выбирается из них");
        BlockCoord best = null;
        int bestDistance = REACH_SQUARED + 1;
        for (BlockCoord cell : walkable) {
            int dx = cell.x() - point.x();
            int dy = cell.y() - point.y();
            int dz = cell.z() - point.z();
            int distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                best = cell;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }
}
