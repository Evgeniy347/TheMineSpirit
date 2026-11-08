// lib: Из Baritone (github.com/cabaletta/baritone, pathing/movement/MovementHelper.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.Objects;
import java.util.OptionalInt;

public final class NavPillarEscape {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private NavPillarEscape() {}

    public static boolean isolated(NavWorld world, int x, int y, int z) {
        Objects.requireNonNull(world, "Мир навигации не может быть null: по нему проверяется опора");
        for (int[] side : SIDES) {
            if (levelMove(world, x + side[0], y, z + side[1])) {
                return false;
            }
        }
        return true;
    }

    public static OptionalInt landingBelow(NavWorld world, int x, int y, int z, int maxDepth) {
        Objects.requireNonNull(world, "Мир навигации не может быть null: по нему ищется приземление");
        if (maxDepth < 1) {
            throw new IllegalArgumentException("Предел спуска " + maxDepth + " меньше одного блока");
        }
        int best = Integer.MIN_VALUE;
        for (int[] side : SIDES) {
            int nx = x + side[0];
            int nz = z + side[1];
            for (int depth = 2; depth <= maxDepth; depth++) {
                int feet = y - depth;
                if (!passableColumn(world, nx, y - 1, feet + 1, nz)) {
                    break;
                }
                boolean landing = world.walkable(nx, feet - 1, nz);
                boolean water = world.water(nx, feet, nz);
                if (landing || water) {
                    best = Math.max(best, feet);
                    break;
                }
            }
        }
        return best == Integer.MIN_VALUE ? OptionalInt.empty() : OptionalInt.of(best);
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
