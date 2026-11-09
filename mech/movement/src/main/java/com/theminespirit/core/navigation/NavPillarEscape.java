// lib: Из Baritone (github.com/cabaletta/baritone, pathing/movement/MovementHelper.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

public final class NavPillarEscape {
    public static final int NARROW_PLATFORM = 4;

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private NavPillarEscape() {}

    public static Set<BlockCoord> plateau(NavWorld world, int x, int y, int z) {
        Objects.requireNonNull(world, "Мир навигации не может быть null: по нему ищется узкая опора");
        if (!levelMove(world, x, y, z)) {
            return Set.of();
        }
        Set<BlockCoord> cells = new LinkedHashSet<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        BlockCoord start = new BlockCoord(x, y, z);
        cells.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockCoord cell = queue.poll();
            for (int[] side : SIDES) {
                BlockCoord next = new BlockCoord(cell.x() + side[0], y, cell.z() + side[1]);
                if (cells.contains(next) || !levelMove(world, next.x(), y, next.z())) {
                    continue;
                }
                cells.add(next);
                if (cells.size() > NARROW_PLATFORM) {
                    return Set.of();
                }
                queue.add(next);
            }
        }
        return Set.copyOf(cells);
    }

    public static OptionalInt landingFrom(NavWorld world, Set<BlockCoord> plateau, int maxDepth) {
        Objects.requireNonNull(world, "Мир навигации не может быть null: по нему ищется приземление");
        Objects.requireNonNull(plateau, "Клетки опоры не могут быть null: без них не от чего прыгать");
        if (maxDepth < 1) {
            throw new IllegalArgumentException("Предел спуска " + maxDepth + " меньше одного блока");
        }
        int best = Integer.MAX_VALUE;
        for (BlockCoord cell : plateau) {
            for (int[] side : SIDES) {
                int nx = cell.x() + side[0];
                int nz = cell.z() + side[1];
                for (int depth = 2; depth <= maxDepth; depth++) {
                    int feet = cell.y() - depth;
                    if (!passableColumn(world, nx, cell.y() - 1, feet, nz)) {
                        break;
                    }
                    boolean landing = world.walkable(nx, feet - 1, nz);
                    boolean water = world.water(nx, feet, nz);
                    if (landing || water) {
                        best = Math.min(best, feet);
                        break;
                    }
                }
            }
        }
        return best == Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of(best);
    }

    private static boolean levelMove(NavWorld world, int x, int y, int z) {
        if (!open(world, x, y, z) || !open(world, x, y + 1, z)) {
            return false;
        }
        return world.walkable(x, y - 1, z) || world.climbable(x, y, z) || world.water(x, y, z);
    }

    private static boolean passableColumn(NavWorld world, int x, int fromY, int toY, int z) {
        for (int y = fromY; y >= toY; y--) {
            if (!world.passable(x, y, z) && !world.water(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private static boolean open(NavWorld world, int x, int y, int z) {
        return world.passable(x, y, z) || world.water(x, y, z) || world.climbable(x, y, z);
    }
}
