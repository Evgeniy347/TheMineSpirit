package com.theminespirit.core.movement;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PitEscape {
    public static final int RADIUS = 6;
    public static final int STEP_UP = 1;
    public static final int SAFE_DROP = 3;

    @FunctionalInterface
    public interface Heights {
        int feet(int dx, int dz);
    }

    public record Exit(int dx, int dz, int rise, int rimDx, int rimDz) {}

    private record Column(int dx, int dz) {}

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private PitEscape() {}

    public static Optional<Exit> exit(Heights heights) {
        Objects.requireNonNull(heights, "Высоты колонок не могут быть null: без них ямы не видно");
        Set<Column> seen = new HashSet<>();
        Deque<Column> queue = new ArrayDeque<>();
        Column start = new Column(0, 0);
        seen.add(start);
        queue.add(start);
        Exit best = null;
        while (!queue.isEmpty()) {
            Column cell = queue.poll();
            if (Math.max(Math.abs(cell.dx()), Math.abs(cell.dz())) >= RADIUS) {
                return Optional.empty();
            }
            int here = heights.feet(cell.dx(), cell.dz());
            for (int[] side : SIDES) {
                Column next = new Column(cell.dx() + side[0], cell.dz() + side[1]);
                int there = heights.feet(next.dx(), next.dz());
                int climb = there - here;
                if (climb <= STEP_UP && climb >= -SAFE_DROP) {
                    if (seen.add(next)) {
                        queue.add(next);
                    }
                } else if (climb > STEP_UP) {
                    int rise = climb - STEP_UP;
                    if (best == null || rise < best.rise()) {
                        best = new Exit(cell.dx(), cell.dz(), rise, next.dx(), next.dz());
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
