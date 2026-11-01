package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

public final class BreathSearch {
    public static final int MAX_CELLS = 512;
    private static final int[][] STEPS = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};

    private BreathSearch() {}

    public static Optional<BlockCoord> nearest(SafetyView view, BlockCoord feet) {
        Objects.requireNonNull(view, "Вид мира не может быть null: без него воздух не найти");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: поиск идёт от них");
        Optional<BlockCoord> dry = search(view, feet, BreathSearch::dryStand, false);
        if (dry.isPresent()) {
            return dry;
        }
        return search(view, feet, BreathSearch::breathable, false);
    }

    public static Optional<BlockCoord> nextStep(SafetyView view, BlockCoord feet) {
        Objects.requireNonNull(view, "Вид мира не может быть null: без него некуда плыть");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: первый шаг считается от них");
        Optional<BlockCoord> dry = search(view, feet, BreathSearch::dryStand, true);
        if (dry.isPresent()) {
            return dry;
        }
        return search(view, feet, BreathSearch::breathable, true);
    }

    private static Optional<BlockCoord> search(
            SafetyView view, BlockCoord feet, BiPredicate<SafetyView, BlockCoord> stop, boolean firstStep) {
        if (stop.test(view, feet)) {
            return Optional.of(feet);
        }
        Deque<BlockCoord> queue = new ArrayDeque<>();
        Set<BlockCoord> seen = new HashSet<>();
        Map<BlockCoord, BlockCoord> parents = new HashMap<>();
        queue.add(feet);
        seen.add(feet);
        while (!queue.isEmpty() && seen.size() <= MAX_CELLS) {
            BlockCoord cell = queue.poll();
            if (stop.test(view, cell)) {
                return Optional.of(firstStep ? firstStepFrom(parents, feet, cell) : cell);
            }
            for (int[] step : STEPS) {
                BlockCoord next = new BlockCoord(cell.x() + step[0], cell.y() + step[1], cell.z() + step[2]);
                if (!seen.contains(next) && swimmableBody(view, next)) {
                    seen.add(next);
                    parents.put(next, cell);
                    queue.add(next);
                }
            }
        }
        return Optional.empty();
    }

    private static BlockCoord firstStepFrom(Map<BlockCoord, BlockCoord> parents, BlockCoord feet, BlockCoord target) {
        BlockCoord cell = target;
        BlockCoord parent = parents.getOrDefault(cell, feet);
        while (!parent.equals(feet)) {
            cell = parent;
            parent = parents.getOrDefault(cell, feet);
        }
        return cell;
    }

    private static boolean dryStand(SafetyView view, BlockCoord cell) {
        return view.cellAt(cell) == Cell.PASSABLE
                && view.cellAt(new BlockCoord(cell.x(), cell.y() + 1, cell.z())) == Cell.PASSABLE
                && MiningSafety.canStandOn(view.cellAt(new BlockCoord(cell.x(), cell.y() - 1, cell.z())))
                && touchesWater(view, cell);
    }

    private static boolean touchesWater(SafetyView view, BlockCoord cell) {
        return view.cellAt(new BlockCoord(cell.x() + 1, cell.y(), cell.z())) == Cell.WATER
                || view.cellAt(new BlockCoord(cell.x() - 1, cell.y(), cell.z())) == Cell.WATER
                || view.cellAt(new BlockCoord(cell.x(), cell.y(), cell.z() + 1)) == Cell.WATER
                || view.cellAt(new BlockCoord(cell.x(), cell.y(), cell.z() - 1)) == Cell.WATER;
    }

    private static boolean breathable(SafetyView view, BlockCoord cell) {
        BlockCoord head = new BlockCoord(cell.x(), cell.y() + 1, cell.z());
        return swimmable(view.cellAt(cell)) && view.cellAt(head) == Cell.PASSABLE;
    }

    private static boolean swimmableBody(SafetyView view, BlockCoord cell) {
        BlockCoord head = new BlockCoord(cell.x(), cell.y() + 1, cell.z());
        return swimmable(view.cellAt(cell)) && swimmable(view.cellAt(head));
    }

    private static boolean swimmable(Cell cell) {
        return cell == Cell.WATER || cell == Cell.PASSABLE;
    }
}
