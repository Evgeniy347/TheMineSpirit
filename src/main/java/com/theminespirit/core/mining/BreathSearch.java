package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class BreathSearch {
    public static final int MAX_CELLS = 512;
    private static final int[][] STEPS = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};

    private BreathSearch() {}

    public static Optional<BlockCoord> nearest(SafetyView view, BlockCoord feet) {
        Objects.requireNonNull(view, "Вид мира не может быть null: без него воздух не найти");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: поиск идёт от них");
        Deque<BlockCoord> queue = new ArrayDeque<>();
        Set<BlockCoord> seen = new HashSet<>();
        queue.add(feet);
        seen.add(feet);
        while (!queue.isEmpty() && seen.size() <= MAX_CELLS) {
            BlockCoord cell = queue.poll();
            if (breathable(view, cell)) {
                return Optional.of(cell);
            }
            for (int[] step : STEPS) {
                BlockCoord next = new BlockCoord(cell.x() + step[0], cell.y() + step[1], cell.z() + step[2]);
                if (!seen.contains(next) && swimmable(view.cellAt(next))) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean breathable(SafetyView view, BlockCoord cell) {
        BlockCoord head = new BlockCoord(cell.x(), cell.y() + 1, cell.z());
        return swimmable(view.cellAt(cell)) && view.cellAt(head) == Cell.PASSABLE;
    }

    private static boolean swimmable(Cell cell) {
        return cell == Cell.WATER || cell == Cell.PASSABLE;
    }
}
