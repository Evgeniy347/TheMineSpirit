package com.theminespirit.core.building;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.movement.BodyStep;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class RetreatPath {
    public static final int MAX_NODES = 4096;
    public static final int WALK_NODES = 32_768;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private RetreatPath() {}

    public static boolean allows(BlockCoord feet, Set<BlockCoord> placed, SafetyView view, Predicate<BlockCoord> exit) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестно, откуда отступать");
        Objects.requireNonNull(placed, "Ставимые клетки не могут быть null: пустое множество задаётся пустым");
        Objects.requireNonNull(view, "Мир не может быть null: по нему ищется путь отхода");
        Objects.requireNonNull(exit, "Выход не может быть null: без него неизвестно, куда отступать");
        if (exit.test(feet) || placed.isEmpty()) {
            return true;
        }
        Reach after = search(feet, placed, view, exit);
        if (after != Reach.CLOSED) {
            return true;
        }
        return search(feet, Set.of(), view, exit) == Reach.CLOSED;
    }

    public static Optional<Set<BlockCoord>> walkable(BlockCoord feet, SafetyView view, Predicate<BlockCoord> within) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестно, откуда идти");
        Objects.requireNonNull(view, "Мир не может быть null: по нему ищется путь");
        Objects.requireNonNull(within, "Граница обхода не может быть null: без неё обход не кончится");
        Set<BlockCoord> seen = new HashSet<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        BlockCoord start = standing(feet, view);
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            if (seen.size() > WALK_NODES) {
                return Optional.empty();
            }
            BlockCoord from = queue.poll();
            for (int[] side : SIDES) {
                BlockCoord next = step(from, side[0], side[1], view);
                if (next != null && within.test(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return Optional.of(Set.copyOf(seen));
    }

    private enum Reach {
        OPEN,
        CLOSED,
        UNKNOWN
    }

    private static Reach search(BlockCoord feet, Set<BlockCoord> placed, SafetyView view, Predicate<BlockCoord> exit) {
        SafetyView overlay = cell -> placed.contains(cell) ? Cell.SOLID : view.cellAt(cell);
        Set<BlockCoord> seen = new HashSet<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        BlockCoord start = standing(feet, view);
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            if (seen.size() > MAX_NODES) {
                return Reach.UNKNOWN;
            }
            BlockCoord from = queue.poll();
            for (int[] side : SIDES) {
                BlockCoord next = step(from, side[0], side[1], overlay);
                if (next == null || !seen.add(next)) {
                    continue;
                }
                if (exit.test(next)) {
                    return Reach.OPEN;
                }
                queue.add(next);
            }
        }
        return Reach.CLOSED;
    }

    private static BlockCoord standing(BlockCoord feet, SafetyView view) {
        BlockCoord above = new BlockCoord(feet.x(), feet.y() + 1, feet.z());
        return !MiningSafety.canStand(feet, view) && MiningSafety.canStand(above, view) ? above : feet;
    }

    private static @Nullable BlockCoord step(BlockCoord from, int dx, int dz, SafetyView view) {
        return BodyStep.step(from, dx, dz, view);
    }
}
