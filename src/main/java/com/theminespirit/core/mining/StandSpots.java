package com.theminespirit.core.mining;

import com.theminespirit.core.building.BlockSight;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.Terrain;
import com.theminespirit.core.tactics.adapter.Visibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class StandSpots {

    private static final int MAX_DEPTH = 2;
    private static final int[][] FACE_OFFSETS = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] HORIZONTAL_OFFSETS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    private StandSpots() {}

    public static List<BlockCoord> of(BlockCoord target, SafetyView view, BlockCoord from) {
        Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, где искать стоянку");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему проверяется стоянка");
        Objects.requireNonNull(from, "Точка отсчёта не может быть null: по ней сортируются стоянки");
        List<BlockCoord> candidates = new ArrayList<>();
        boolean exposed = false;
        boolean fallingAbove = view.cellAt(offset(target, 0, 1, 0)) == Cell.FALLING;
        for (int[] face : FACE_OFFSETS) {
            BlockCoord neighbor = offset(target, face[0], face[1], face[2]);
            if (view.cellAt(neighbor) != Cell.PASSABLE) {
                continue;
            }
            exposed = true;
            for (int depth = 0; depth <= MAX_DEPTH; depth++) {
                BlockCoord stand = new BlockCoord(neighbor.x(), neighbor.y() - depth, neighbor.z());
                if (offset(stand, 0, -1, 0).equals(target)) {
                    continue;
                }
                if (fallingAbove && stand.x() == target.x() && stand.z() == target.z() && stand.y() < target.y()) {
                    continue;
                }
                if (MiningSafety.canStand(stand, view) && visible(stand, target, view)) {
                    candidates.add(stand);
                }
            }
            if (face[1] == 1) {
                for (int[] side : HORIZONTAL_OFFSETS) {
                    BlockCoord stand = offset(neighbor, side[0], 0, side[2]);
                    if (MiningSafety.canStand(stand, view) && visible(stand, target, view)) {
                        candidates.add(stand);
                    }
                }
            }
        }
        if (!exposed) {
            return List.of();
        }
        List<BlockCoord> unique = new ArrayList<>(new LinkedHashSet<>(candidates));
        Comparator<BlockCoord> byDistance = Comparator.comparingLong(coord -> distanceSquared(from, coord));
        unique.sort(byDistance.thenComparing(Comparator.naturalOrder()));
        return List.copyOf(unique);
    }

    private static boolean visible(BlockCoord stand, BlockCoord target, SafetyView view) {
        return BlockSight.visible(BlockSight.eyes(stand), target, coord -> terrain(view.cellAt(coord)), cell -> false)
                == Visibility.CLEAR;
    }

    private static Terrain terrain(Cell cell) {
        return switch (cell) {
            case SOLID, FALLING, HAZARD, PORTAL -> Terrain.SOLID;
            case WATER -> Terrain.WATER;
            case LAVA -> Terrain.LAVA;
            case PASSABLE, FIRE -> Terrain.AIR;
        };
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static BlockCoord offset(BlockCoord coord, int dx, int dy, int dz) {
        return new BlockCoord(coord.x() + dx, coord.y() + dy, coord.z() + dz);
    }
}
