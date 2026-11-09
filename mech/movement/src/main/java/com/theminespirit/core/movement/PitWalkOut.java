package com.theminespirit.core.movement;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

public final class PitWalkOut {
    public static final int EXIT_RADIUS = 6;
    private static final int STEP_UP = 1;
    private static final int SAFE_DROP = 3;
    private static final int SURFACE_CHECK_DISTANCE = 3;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public interface World {
        OptionalInt groundY(int x, int z, int from);

        int surfaceY(int x, int z);

        boolean clear(int x, int y, int z);
    }

    private PitWalkOut() {}

    public static boolean exists(World world, int startX, int startZ, int startY) {
        Objects.requireNonNull(world, "Мир не может быть null: без него не видно ни пола, ни неба");
        OptionalInt start = world.groundY(startX, startZ, startY);
        if (start.isEmpty()) {
            return false;
        }
        Set<Long> seen = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        int[] origin = {startX, startZ, start.getAsInt()};
        seen.add(key(0, 0));
        queue.add(origin);
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (int[] side : SIDES) {
                int nx = cell[0] + side[0];
                int nz = cell[1] + side[1];
                OptionalInt ground = world.groundY(nx, nz, cell[2]);
                if (ground.isEmpty()) {
                    continue;
                }
                int climb = ground.getAsInt() - cell[2];
                boolean headroom = climb <= 0 || world.clear(cell[0], cell[2] + 2, cell[1]);
                boolean walkable = climb <= STEP_UP && climb >= -SAFE_DROP && headroom;
                if (!walkable) {
                    continue;
                }
                int ndx = nx - startX;
                int ndz = nz - startZ;
                if (Math.abs(ndx) >= EXIT_RADIUS || Math.abs(ndz) >= EXIT_RADIUS) {
                    if (surfaceExit(world, nx, ground.getAsInt(), nz)) {
                        return true;
                    }
                    continue;
                }
                if (seen.add(key(ndx, ndz))) {
                    queue.add(new int[] {nx, nz, ground.getAsInt()});
                }
            }
        }
        return false;
    }

    private static boolean surfaceExit(World world, int x, int y, int z) {
        if (world.surfaceY(x, z) > y) {
            return false;
        }
        for (int[] side : SIDES) {
            int farX = x + SURFACE_CHECK_DISTANCE * side[0];
            int farZ = z + SURFACE_CHECK_DISTANCE * side[1];
            if (world.surfaceY(farX, farZ) <= y + SurfaceLevel.MARGIN) {
                return true;
            }
        }
        return false;
    }

    private static long key(int dx, int dz) {
        return (((long) dx) << 32) ^ (dz & 0xffffffffL);
    }
}
