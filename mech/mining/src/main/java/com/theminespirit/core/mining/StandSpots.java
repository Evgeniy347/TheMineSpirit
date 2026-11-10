package com.theminespirit.core.mining;

import com.theminespirit.core.building.BlockSight;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.adapter.Visibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    public static Optional<BlockCoord> above(BlockCoord target, SafetyView view) {
        Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, где искать стоянку сверху");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему проверяется стоянка");
        if (view.cellAt(target) != Cell.SOLID) {
            return Optional.empty();
        }
        BlockCoord feet = new BlockCoord(target.x(), target.y() + 1, target.z());
        if (view.cellAt(feet) != Cell.PASSABLE
                || view.cellAt(new BlockCoord(feet.x(), feet.y() + 1, feet.z())) != Cell.PASSABLE) {
            return Optional.empty();
        }
        return Optional.of(feet);
    }

    public static boolean walkable(BlockCoord stand, BlockCoord from) {
        Objects.requireNonNull(stand, "Стоянка не может быть null: без неё непонятно, что проверять на подъём");
        Objects.requireNonNull(from, "Точка духа не может быть null: по ней считается высота подъёма");
        return stand.y() - from.y() <= MiningTuning.MAX_STAND_RISE;
    }

    private static boolean visible(BlockCoord stand, BlockCoord target, SafetyView view) {
        return BlockSight.visible(
                        BlockSight.eyes(stand), target, coord -> MineTerrain.of(view.cellAt(coord)), cell -> false)
                == Visibility.CLEAR;
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
