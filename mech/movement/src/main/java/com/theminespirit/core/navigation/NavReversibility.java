// lib: Из Baritone (github.com/cabaletta/baritone, pathing/movement/MovementHelper.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class NavReversibility {
    public static final int MAX_NODES = 512;
    public static final int MAX_HORIZONTAL = 16;
    public static final int MAX_ABOVE_REFERENCE = 4;

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private NavReversibility() {}

    public static boolean canReturn(NavWorld world, int x, int y, int z, int referenceY) {
        Objects.requireNonNull(world, "Мир навигации не может быть null: по нему проверяется выход из спуска");
        if (y >= referenceY) {
            return true;
        }
        Set<Long> seen = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        int[] start = {x, y, z};
        seen.add(NavNode.key(x, y, z));
        queue.add(start);
        int visits = 0;
        while (!queue.isEmpty() && visits++ < MAX_NODES) {
            int[] cell = queue.poll();
            for (int[] side : SIDES) {
                int[] next = twoWay(world, cell[0], cell[1], cell[2], side[0], side[1]);
                if (next == null) {
                    continue;
                }
                if (Math.abs(next[0] - x) > MAX_HORIZONTAL || Math.abs(next[2] - z) > MAX_HORIZONTAL) {
                    continue;
                }
                if (next[1] > referenceY + MAX_ABOVE_REFERENCE) {
                    continue;
                }
                if (next[1] >= referenceY) {
                    return true;
                }
                if (seen.add(NavNode.key(next[0], next[1], next[2]))) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    private static int @Nullable [] twoWay(NavWorld world, int x, int y, int z, int dx, int dz) {
        int[] next = step(world, x, y, z, dx, dz);
        if (next == null) {
            return null;
        }
        int[] back = step(world, next[0], next[1], next[2], -dx, -dz);
        return back != null && back[0] == x && back[1] == y && back[2] == z ? next : null;
    }

    private static int @Nullable [] step(NavWorld world, int x, int y, int z, int dx, int dz) {
        int nx = x + dx;
        int nz = z + dz;
        if (stand(world, nx, y, nz) && passableBody(world, nx, y, nz)) {
            return new int[] {nx, y, nz};
        }
        if (open(world, x, y + 2, z) && stand(world, nx, y + 1, nz) && passableBody(world, nx, y + 1, nz)) {
            return new int[] {nx, y + 1, nz};
        }
        if (stand(world, nx, y - 1, nz) && passableBody(world, nx, y - 1, nz)) {
            return new int[] {nx, y - 1, nz};
        }
        return null;
    }

    private static boolean stand(NavWorld world, int x, int y, int z) {
        return world.walkable(x, y - 1, z) || world.climbable(x, y, z) || world.water(x, y, z);
    }

    private static boolean passableBody(NavWorld world, int x, int y, int z) {
        return open(world, x, y, z) && open(world, x, y + 1, z);
    }

    private static boolean open(NavWorld world, int x, int y, int z) {
        return world.passable(x, y, z) || world.water(x, y, z) || world.climbable(x, y, z);
    }
}
