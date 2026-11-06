package com.theminespirit.core.movement;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;

public final class LineObstacle {
    private static final double STEP = 0.25;

    private LineObstacle() {}

    public static Optional<BlockCoord> first(BlockCoord from, BlockCoord to, SafetyView view) {
        Objects.requireNonNull(from, "Начало линии не может быть null: без него не найти преграду");
        Objects.requireNonNull(to, "Конец линии не может быть null: без него не найти преграду");
        Objects.requireNonNull(view, "Вид мира не может быть null: по нему ищется преграда");
        double sx = from.x() + 0.5;
        double sy = from.y() + 0.5;
        double sz = from.z() + 0.5;
        double dx = to.x() + 0.5 - sx;
        double dy = to.y() + 0.5 - sy;
        double dz = to.z() + 0.5 - sz;
        int steps = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / STEP);
        BlockCoord previous = from;
        for (int step = 1; step <= steps; step++) {
            double ratio = (double) step / steps;
            BlockCoord cell = new BlockCoord((int) Math.floor(sx + dx * ratio), (int) Math.floor(sy + dy * ratio), (int)
                    Math.floor(sz + dz * ratio));
            if (cell.equals(previous) || cell.equals(to) || cell.equals(from)) {
                continue;
            }
            previous = cell;
            if (view.cellAt(cell) == Cell.SOLID) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }
}
