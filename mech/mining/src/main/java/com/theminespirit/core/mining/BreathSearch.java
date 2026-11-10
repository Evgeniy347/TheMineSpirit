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
    static final int MAX_SURFACE_CELLS = 8192;
    private static final int MAX_SURFACE_UP = 64;
    private static final int[][] STEPS = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
    private static final int[][] ALONG_SURFACE = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    private BreathSearch() {}

    public static Optional<BlockCoord> nearest(SafetyView view, BlockCoord feet) {
        Objects.requireNonNull(view, "Вид мира не может быть null: без него воздух не найти");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: поиск идёт от них");
        Optional<BlockCoord> surface = surface(view, feet);
        if (surface.isPresent()) {
            Optional<BlockCoord> shore = surfaceShore(view, surface.get());
            if (shore.isPresent()) {
                return shore;
            }
        }
        Optional<BlockCoord> dry = search(view, feet, BreathSearch::dryStand, false, MAX_CELLS, STEPS);
        if (dry.isPresent()) {
            return dry;
        }
        if (surface.isPresent() && breathable(view, surface.get())) {
            return surface;
        }
        return search(view, feet, BreathSearch::breathable, false, MAX_CELLS, STEPS);
    }

    public static Optional<BlockCoord> nextStep(SafetyView view, BlockCoord feet) {
        Objects.requireNonNull(view, "Вид мира не может быть null: без него некуда плыть");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: первый шаг считается от них");
        Optional<BlockCoord> surface = surface(view, feet);
        if (surface.isPresent() && !surface.get().equals(feet)) {
            return Optional.of(new BlockCoord(feet.x(), feet.y() + 1, feet.z()));
        }
        Optional<BlockCoord> around = search(view, feet, BreathSearch::dryStand, true, MAX_CELLS, STEPS);
        if (around.isPresent()) {
            return around;
        }
        return search(view, feet, BreathSearch::breathable, true, MAX_CELLS, STEPS);
    }

    private static Optional<BlockCoord> surfaceShore(SafetyView view, BlockCoord surface) {
        Deque<BlockCoord> queue = new ArrayDeque<>();
        Set<BlockCoord> seen = new HashSet<>();
        queue.add(surface);
        seen.add(surface);
        while (!queue.isEmpty() && seen.size() <= MAX_SURFACE_CELLS) {
            BlockCoord cell = queue.poll();
            Optional<BlockCoord> stand = shoreNextTo(view, cell);
            if (stand.isPresent()) {
                return stand;
            }
            for (int[] step : ALONG_SURFACE) {
                BlockCoord next = new BlockCoord(cell.x() + step[0], cell.y(), cell.z() + step[2]);
                if (!seen.contains(next)
                        && view.cellAt(next) == Cell.WATER
                        && view.cellAt(new BlockCoord(next.x(), next.y() + 1, next.z())) == Cell.PASSABLE) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockCoord> shoreNextTo(SafetyView view, BlockCoord water) {
        for (int[] step : ALONG_SURFACE) {
            BlockCoord neighbour = new BlockCoord(water.x() + step[0], water.y(), water.z() + step[2]);
            if (dryStand(view, neighbour)) {
                return Optional.of(neighbour);
            }
            if (view.cellAt(neighbour) == Cell.SOLID) {
                BlockCoord top = new BlockCoord(neighbour.x(), neighbour.y() + 1, neighbour.z());
                if (roomToClimb(view, water) && dryStand(view, top)) {
                    return Optional.of(top);
                }
            }
        }
        return Optional.empty();
    }

    // lib: на берег выше воды дух попадает прыжком; прыжок требует места над головой, иначе он упирается
    // lib: в потолок и вечно плывёт в стену (живой случай: -99,52,-417, вода под землёй, потолок y=54).
    private static boolean roomToClimb(SafetyView view, BlockCoord water) {
        return view.cellAt(new BlockCoord(water.x(), water.y() + 2, water.z())) == Cell.PASSABLE;
    }

    private static Optional<BlockCoord> surface(SafetyView view, BlockCoord feet) {
        BlockCoord cell = feet;
        for (int step = 0; step < MAX_SURFACE_UP; step++) {
            if (view.cellAt(cell) != Cell.WATER) {
                return Optional.of(cell);
            }
            BlockCoord above = new BlockCoord(cell.x(), cell.y() + 1, cell.z());
            if (view.cellAt(above) != Cell.WATER) {
                return view.cellAt(above) == Cell.PASSABLE ? Optional.of(cell) : Optional.empty();
            }
            if (!swimmableBody(view, above)) {
                return Optional.empty();
            }
            cell = above;
        }
        return Optional.empty();
    }

    private static Optional<BlockCoord> search(
            SafetyView view,
            BlockCoord feet,
            BiPredicate<SafetyView, BlockCoord> stop,
            boolean firstStep,
            int maxCells,
            int[][] steps) {
        if (stop.test(view, feet)) {
            return Optional.of(feet);
        }
        Deque<BlockCoord> queue = new ArrayDeque<>();
        Set<BlockCoord> seen = new HashSet<>();
        Map<BlockCoord, BlockCoord> parents = new HashMap<>();
        queue.add(feet);
        seen.add(feet);
        while (!queue.isEmpty() && seen.size() <= maxCells) {
            BlockCoord cell = queue.poll();
            if (stop.test(view, cell)) {
                return Optional.of(firstStep ? firstStepFrom(parents, feet, cell) : cell);
            }
            for (int[] step : steps) {
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
        return sideWater(view, cell.x(), cell.y(), cell.z()) || sideWater(view, cell.x(), cell.y() - 1, cell.z());
    }

    private static boolean sideWater(SafetyView view, int x, int y, int z) {
        return view.cellAt(new BlockCoord(x + 1, y, z)) == Cell.WATER
                || view.cellAt(new BlockCoord(x - 1, y, z)) == Cell.WATER
                || view.cellAt(new BlockCoord(x, y, z + 1)) == Cell.WATER
                || view.cellAt(new BlockCoord(x, y, z - 1)) == Cell.WATER;
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
