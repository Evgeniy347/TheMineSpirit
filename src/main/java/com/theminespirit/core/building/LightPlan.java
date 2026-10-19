package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * План освещения территории факелами: по карте блочного света выбирает клетки под факелы так, чтобы на земле
 * не осталось тёмных клеток (блочный свет не выше порога спавна враждебных мобов).
 */
public final class LightPlan {
    public static final int MOB_SPAWN_LIGHT = 0;
    public static final int TORCH_LIGHT_LEVEL = 14;
    public static final int TORCH_REACH = TORCH_LIGHT_LEVEL - 1;
    public static final int GRID_STEP = TORCH_REACH / 2;

    public record Plan(List<BlockCoord> torches, int darkRemaining, int darkTotal) {
        public Plan {
            torches = List.copyOf(Objects.requireNonNull(torches, "Список факелов не может быть null"));
            if (darkRemaining < 0 || darkTotal < 0 || darkRemaining > darkTotal) {
                throw new IllegalArgumentException("Тёмных клеток осталось " + darkRemaining + " из " + darkTotal
                        + ": счёт не может быть отрицательным или больше общего числа");
            }
        }
    }

    private LightPlan() {}

    public static Plan plan(int centerX, int centerZ, int radius, List<LightCell> cells) {
        Objects.requireNonNull(cells, "Карта света не может быть null: по ней ставится план");
        checkRadius(radius);
        List<BlockCoord> candidates = grid(centerX, centerZ, radius, cells);
        List<BlockCoord> dark = new ArrayList<>();
        for (LightCell cell : cells) {
            if (cell.light() <= MOB_SPAWN_LIGHT) {
                dark.add(cell.coord());
            }
        }
        Set<BlockCoord> uncovered = new LinkedHashSet<>(dark);
        Set<BlockCoord> chosen = new LinkedHashSet<>();
        while (!uncovered.isEmpty()) {
            BlockCoord best = null;
            List<BlockCoord> bestCover = List.of();
            for (BlockCoord candidate : candidates) {
                if (chosen.contains(candidate)) {
                    continue;
                }
                List<BlockCoord> cover = covered(candidate, uncovered);
                if (cover.size() > bestCover.size()) {
                    best = candidate;
                    bestCover = cover;
                }
            }
            if (best == null || bestCover.isEmpty()) {
                break;
            }
            chosen.add(best);
            uncovered.removeAll(bestCover);
        }
        List<BlockCoord> torches = new ArrayList<>(chosen);
        torches.sort(Comparator.naturalOrder());
        return new Plan(torches, uncovered.size(), dark.size());
    }

    public static List<BlockCoord> grid(int centerX, int centerZ, int radius, List<LightCell> cells) {
        Objects.requireNonNull(cells, "Карта света не может быть null: по ней строится сетка");
        checkRadius(radius);
        Map<Long, LightCell> byColumn = index(cells);
        Set<BlockCoord> found = new LinkedHashSet<>();
        for (int gx : lines(centerX, radius)) {
            for (int gz : lines(centerZ, radius)) {
                LightCell chosen = placeableFor(gx, gz, byColumn);
                if (chosen != null) {
                    found.add(chosen.coord());
                }
            }
        }
        List<BlockCoord> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.naturalOrder());
        return List.copyOf(sorted);
    }

    public static boolean withinReach(BlockCoord torch, BlockCoord cell) {
        return Math.abs(torch.x() - cell.x()) + Math.abs(torch.z() - cell.z()) <= TORCH_REACH;
    }

    private static List<BlockCoord> covered(BlockCoord torch, Set<BlockCoord> dark) {
        List<BlockCoord> hit = new ArrayList<>();
        for (BlockCoord cell : dark) {
            if (withinReach(torch, cell)) {
                hit.add(cell);
            }
        }
        return hit;
    }

    private static LightCell placeableFor(int gx, int gz, Map<Long, LightCell> byColumn) {
        LightCell exact = byColumn.get(key(gx, gz));
        if (exact != null && exact.placeable()) {
            return exact;
        }
        LightCell best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dx = -TORCH_REACH; dx <= TORCH_REACH; dx++) {
            for (int dz = -TORCH_REACH; dz <= TORCH_REACH; dz++) {
                int distance = Math.abs(dx) + Math.abs(dz);
                if (distance > TORCH_REACH || distance > bestDistance) {
                    continue;
                }
                LightCell candidate = byColumn.get(key(gx + dx, gz + dz));
                if (candidate == null || !candidate.placeable()) {
                    continue;
                }
                if (distance < bestDistance || isEarlier(candidate.coord(), best)) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static boolean isEarlier(BlockCoord candidate, LightCell current) {
        return current == null || candidate.compareTo(current.coord()) < 0;
    }

    private static List<Integer> lines(int center, int radius) {
        int half = radius / GRID_STEP;
        List<Integer> result = new ArrayList<>(2 * half + 1);
        for (int step = -half; step <= half; step++) {
            result.add(center + step * GRID_STEP);
        }
        return result;
    }

    private static Map<Long, LightCell> index(List<LightCell> cells) {
        Map<Long, LightCell> byColumn = new HashMap<>();
        for (LightCell cell : cells) {
            byColumn.put(key(cell.coord().x(), cell.coord().z()), cell);
        }
        return byColumn;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static void checkRadius(int radius) {
        if (radius < 1) {
            throw new IllegalArgumentException("Радиус области " + radius + " меньше 1: освещать нечего");
        }
    }
}
