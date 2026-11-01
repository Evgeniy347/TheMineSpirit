package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SelfEscape {
    public enum Method {
        DIG,
        STEP,
        DESCEND,
        GOTO
    }

    public record Plan(Method method, BlockCoord target, int rise, List<BlockCoord> blocks) {
        public Plan {
            Objects.requireNonNull(method, "Способ выхода не может быть null: без него неизвестно, копать или ставить");
            Objects.requireNonNull(target, "Точка выхода не может быть null: духу некуда идти после спасения");
            if (rise < 0) {
                throw new IllegalArgumentException("Подъём " + rise + " отрицателен: ступени не ставят вниз");
            }
            blocks = List.copyOf(Objects.requireNonNull(
                    blocks, "Список блоков на слом не может быть null: выход без блоков задают пустым списком"));
            if (method == Method.STEP && rise < 1) {
                throw new IllegalArgumentException(
                        "Шаг без подъёма (rise = " + rise + "): ступень ставят только когда край выше духа");
            }
            if (method == Method.DIG && blocks.isEmpty() && rise < 1) {
                throw new IllegalArgumentException("Копание без блоков и без подъёма: такой выход ничего не меняет");
            }
        }
    }

    private static final List<int[]> SIDES =
            List.of(new int[] {1, 0}, new int[] {-1, 0}, new int[] {0, 1}, new int[] {0, -1});
    private static final int NEARBY_RADIUS = 24;

    private SelfEscape() {}

    public static Optional<Plan> nearby(BlockCoord feet, StuckSituation.Grid grid) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: от них ищется соседняя клетка");
        Objects.requireNonNull(grid, "Сетка клеток не может быть null: без неё не видно прохода");
        if (StuckSituation.detect(feet, grid).stuck()) {
            return Optional.empty();
        }
        for (int[] side : SIDES) {
            Optional<BlockCoord> cell = open(feet, grid, side[0], side[1]);
            if (cell.isPresent()) {
                return Optional.of(new Plan(Method.GOTO, cell.orElseThrow(), 0, List.of()));
            }
        }
        for (int radius = 1; radius <= NEARBY_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius || dx == 0 || dz == 0) {
                        continue;
                    }
                    Optional<BlockCoord> cell = open(feet, grid, dx, dz);
                    if (cell.isPresent()) {
                        return Optional.of(new Plan(Method.GOTO, cell.orElseThrow(), 0, List.of()));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockCoord> open(BlockCoord feet, StuckSituation.Grid grid, int dx, int dz) {
        BlockCoord lower = new BlockCoord(feet.x() + dx, feet.y(), feet.z() + dz);
        BlockCoord upper = new BlockCoord(lower.x(), lower.y() + 1, lower.z());
        BlockCoord below = new BlockCoord(lower.x(), lower.y() - 1, lower.z());
        if (grid.leaf(lower) || grid.leaf(upper) || grid.leaf(below)) {
            return Optional.empty();
        }
        return grid.passable(lower) && grid.passable(upper) ? Optional.of(lower) : Optional.empty();
    }

    public static Optional<Plan> plan(
            BlockCoord feet, BlockCoord goal, StuckSituation.Grid grid, boolean tool, int stepBlocks) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: от них строится выход");
        Objects.requireNonNull(goal, "Цель движения не может быть null: в её сторону выбирается проход");
        Objects.requireNonNull(grid, "Сетка клеток не может быть null: без неё не видно, что копать");
        if (stepBlocks < 0) {
            throw new IllegalArgumentException(
                    "Запас ступеней " + stepBlocks + " отрицателен: число блоков в инвентаре не бывает меньше нуля");
        }
        StuckSituation.Verdict verdict = StuckSituation.detect(feet, grid);
        if (!verdict.stuck()) {
            if (goal.y() < feet.y()) {
                return Optional.of(new Plan(Method.DESCEND, goal, 0, List.of()));
            }
            return Optional.empty();
        }
        if (verdict.kind() == StuckSituation.Kind.DEEP_PIT) {
            PitEscape.Exit exit = Objects.requireNonNull(
                    verdict.pit(), "Глубокая яма без выхода: правило ямы обязано назвать край для подъёма");
            return pit(feet, exit, grid, tool, stepBlocks);
        }
        return tool ? dig(feet, goal, grid) : Optional.empty();
    }

    private static Optional<Plan> pit(
            BlockCoord feet, PitEscape.Exit exit, StuckSituation.Grid grid, boolean tool, int stepBlocks) {
        BlockCoord rim = new BlockCoord(feet.x() + exit.rimDx(), feet.y() + exit.rise() + 1, feet.z() + exit.rimDz());
        if (stepBlocks >= exit.rise()) {
            return Optional.of(new Plan(Method.STEP, rim, exit.rise(), List.of()));
        }
        if (!tool) {
            return Optional.empty();
        }
        List<BlockCoord> blocks = new ArrayList<>();
        for (int dy = 1; dy <= StuckSituation.PASSAGE_HEIGHT; dy++) {
            BlockCoord cell = new BlockCoord(feet.x(), feet.y() + dy, feet.z());
            if (!grid.passable(cell)) {
                blocks.add(cell);
            }
        }
        if (blocks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Plan(Method.DIG, rim, exit.rise(), blocks));
    }

    private static Optional<Plan> dig(BlockCoord feet, BlockCoord goal, StuckSituation.Grid grid) {
        for (int[] side : toward(goal.x() - feet.x(), goal.z() - feet.z())) {
            BlockCoord ahead = new BlockCoord(feet.x() + side[0], feet.y(), feet.z() + side[1]);
            BlockCoord above = new BlockCoord(ahead.x(), ahead.y() + 1, ahead.z());
            List<BlockCoord> blocks = new ArrayList<>();
            if (!grid.passable(ahead)) {
                blocks.add(ahead);
            }
            if (!grid.passable(above)) {
                blocks.add(above);
            }
            if (!blocks.isEmpty()) {
                return Optional.of(new Plan(Method.DIG, ahead, 0, blocks));
            }
        }
        return Optional.empty();
    }

    private static List<int[]> toward(int dx, int dz) {
        int signX = Integer.signum(dx);
        int signZ = Integer.signum(dz);
        List<int[]> sorted = new ArrayList<>(SIDES);
        sorted.sort(Comparator.comparingInt((int[] side) -> -(side[0] * signX + side[1] * signZ)));
        return sorted;
    }
}
