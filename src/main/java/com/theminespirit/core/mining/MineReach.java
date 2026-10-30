package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.DropPolicy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class MineReach {
    public static final int MAX_CELLS = 256;
    public static final int MAX_RADIUS = 8;
    public static final int MAX_FALL = DropPolicy.MAX_DROP;

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private MineReach() {}

    public static boolean canReach(BlockCoord from, BlockCoord target, SafetyView view) {
        Objects.requireNonNull(from, "Точка духа не может быть null: без неё неизвестно, откуда идти");
        Objects.requireNonNull(target, "Цель не может быть null: её проверяют на достижимость пешком");
        Objects.requireNonNull(view, "Вид мира не может быть null: по нему проверяются шаги");
        return stands(from, view).contains(target);
    }

    public static Set<BlockCoord> stands(BlockCoord from, SafetyView view) {
        Objects.requireNonNull(from, "Точка духа не может быть null: с неё начинается обход достижимых клеток");
        Objects.requireNonNull(view, "Вид мира не может быть null: без него не видно стен и пола");
        Set<BlockCoord> seen = new LinkedHashSet<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        seen.add(from);
        queue.add(from);
        while (!queue.isEmpty() && seen.size() < MAX_CELLS) {
            BlockCoord at = queue.poll();
            for (int[] side : SIDES) {
                expand(from, at, side, view, seen, queue);
            }
        }
        return seen;
    }

    private static void expand(
            BlockCoord origin,
            BlockCoord at,
            int[] side,
            SafetyView view,
            Set<BlockCoord> seen,
            Deque<BlockCoord> queue) {
        int x = at.x() + side[0];
        int z = at.z() + side[1];
        if (!inside(origin, x, at.y(), z)) {
            return;
        }
        BlockCoord same = new BlockCoord(x, at.y(), z);
        if (view.cellAt(same) == Cell.PASSABLE) {
            if (MiningSafety.canStand(same, view)) {
                offer(origin, same, seen, queue);
            } else {
                fall(origin, same, view, seen, queue);
            }
        }
        if (view.cellAt(offset(at, 0, 1, 0)) != Cell.PASSABLE) {
            return;
        }
        BlockCoord up = new BlockCoord(x, at.y() + 1, z);
        if (inside(origin, up) && MiningSafety.canStand(up, view)) {
            offer(origin, up, seen, queue);
        }
    }

    private static void fall(
            BlockCoord origin, BlockCoord edge, SafetyView view, Set<BlockCoord> seen, Deque<BlockCoord> queue) {
        for (int depth = 1; depth <= MAX_FALL; depth++) {
            BlockCoord landing = offset(edge, 0, -depth, 0);
            if (!inside(origin, landing)) {
                return;
            }
            if (MiningSafety.canStand(landing, view)) {
                offer(origin, landing, seen, queue);
                return;
            }
            if (view.cellAt(landing) != Cell.PASSABLE) {
                return;
            }
        }
    }

    private static void offer(BlockCoord origin, BlockCoord cell, Set<BlockCoord> seen, Deque<BlockCoord> queue) {
        if (seen.size() >= MAX_CELLS || !inside(origin, cell)) {
            return;
        }
        if (seen.add(cell)) {
            queue.add(cell);
        }
    }

    private static boolean inside(BlockCoord origin, BlockCoord cell) {
        return inside(origin, cell.x(), cell.y(), cell.z());
    }

    private static boolean inside(BlockCoord origin, int x, int y, int z) {
        return Math.abs(x - origin.x()) <= MAX_RADIUS
                && Math.abs(y - origin.y()) <= MAX_RADIUS
                && Math.abs(z - origin.z()) <= MAX_RADIUS;
    }

    private static BlockCoord offset(BlockCoord coord, int dx, int dy, int dz) {
        return new BlockCoord(coord.x() + dx, coord.y() + dy, coord.z() + dz);
    }
}
