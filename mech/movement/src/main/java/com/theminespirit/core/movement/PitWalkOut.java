package com.theminespirit.core.movement;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

public final class PitWalkOut {
    public static final int EXIT_RADIUS = 6;
    private static final int STEP_UP = 1;
    private static final int SAFE_DROP = 3;
    private static final int UNKNOWN_HEIGHT = Integer.MIN_VALUE;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public interface World {
        OptionalInt groundY(int x, int z, int from);

        int surfaceY(int x, int z);

        boolean clear(int x, int y, int z);
    }

    private PitWalkOut() {}

    public static boolean exists(World world, WalkOutRules rules, int startX, int startZ, int startY) {
        Objects.requireNonNull(world, "Мир не может быть null: без него не видно ни пола, ни неба");
        Objects.requireNonNull(rules, "Правила выхода не могут быть null: без них неизвестна область проверки");
        OptionalInt start = world.groundY(startX, startZ, startY);
        if (start.isEmpty()) {
            return false;
        }
        Surface surface = new Surface(world, rules.surfaceSpread(), startX, startZ);
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
                OptionalInt ground = PitWalkStep.next(world, nx, nz, cell[2], SAFE_DROP);
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
                    if (surface.exit(nx, ground.getAsInt(), nz)) {
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

    private static long key(int dx, int dz) {
        return (((long) dx) << 32) ^ (dz & 0xffffffffL);
    }

    private static final class Surface {
        private final World world;
        private final int spread;
        private final int half;
        private final int size;
        private final int originX;
        private final int originZ;
        private final int[] heights;
        private final int[] marks;
        private final int[] queueX;
        private final int[] queueZ;
        private int mark;

        private Surface(World world, int spread, int originX, int originZ) {
            this.world = world;
            this.spread = spread;
            this.half = EXIT_RADIUS + spread;
            this.size = 2 * half + 1;
            this.originX = originX;
            this.originZ = originZ;
            this.heights = new int[size * size];
            Arrays.fill(heights, UNKNOWN_HEIGHT);
            this.marks = new int[size * size];
            int cells = (2 * spread + 1) * (2 * spread + 1);
            this.queueX = new int[cells];
            this.queueZ = new int[cells];
        }

        private boolean exit(int x, int y, int z) {
            if (height(x, z) > y) {
                return false;
            }
            return open(x, y, z);
        }

        private int height(int x, int z) {
            int dx = x - originX;
            int dz = z - originZ;
            if (Math.abs(dx) > half || Math.abs(dz) > half) {
                return world.surfaceY(x, z);
            }
            int index = index(dx, dz);
            int value = heights[index];
            if (value == UNKNOWN_HEIGHT) {
                value = world.surfaceY(x, z);
                heights[index] = value;
            }
            return value;
        }

        private boolean open(int centerX, int centerY, int centerZ) {
            mark++;
            int cells = queueX.length;
            int head = 0;
            int tail = 0;
            markCell(centerX, centerZ);
            queueX[tail] = centerX;
            queueZ[tail] = centerZ;
            tail++;
            int visited = 0;
            while (head < tail && visited < cells) {
                int x = queueX[head];
                int z = queueZ[head];
                head++;
                visited++;
                if (Math.abs(x - centerX) >= spread || Math.abs(z - centerZ) >= spread) {
                    return true;
                }
                for (int[] side : SIDES) {
                    int nx = x + side[0];
                    int nz = z + side[1];
                    if (!markCell(nx, nz)) {
                        continue;
                    }
                    if (height(nx, nz) <= centerY + SurfaceLevel.MARGIN) {
                        queueX[tail] = nx;
                        queueZ[tail] = nz;
                        tail++;
                    }
                }
            }
            return false;
        }

        private boolean markCell(int x, int z) {
            int index = index(x - originX, z - originZ);
            if (marks[index] == mark) {
                return false;
            }
            marks[index] = mark;
            return true;
        }

        private int index(int dx, int dz) {
            return (dx + half) * size + (dz + half);
        }
    }
}
