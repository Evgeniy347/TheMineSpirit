package com.theminespirit.core.farming;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class FarmStandSpots {
    public static final int MAX_SAFETY_READS = 324;

    private static final int STAND_RADIUS = 3;
    private static final double EYE_HEIGHT = 1.62;
    private static final double MAX_REACH_SQUARED = 16.0;

    private FarmStandSpots() {
    }

    public static List<BlockCoord> of(BlockCoord target, BlockCoord from, SafetyView safety, FarmView farmView) {
        Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, где искать стоянку");
        Objects.requireNonNull(from, "Точка духа не может быть null: по ней сортируются стоянки");
        Objects.requireNonNull(safety, "SafetyView не может быть null: по нему проверяется пол и опасные соседи");
        Objects.requireNonNull(farmView, "FarmView не может быть null: по нему стоянка не топчет грядку");
        List<Stand> stands = new ArrayList<>();
        for (int dx = -STAND_RADIUS; dx <= STAND_RADIUS; dx++) {
            for (int dz = -STAND_RADIUS; dz <= STAND_RADIUS; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = 1; dy <= 2; dy++) {
                    BlockCoord feet = new BlockCoord(target.x() + dx, target.y() + dy, target.z() + dz);
                    if (!withinReach(feet, target) || !canStand(feet, safety)) {
                        continue;
                    }
                    BlockCoord floor = new BlockCoord(feet.x(), feet.y() - 1, feet.z());
                    boolean farmland = farmView.tileAt(floor).ground() == Ground.FARMLAND;
                    stands.add(new Stand(feet, farmland));
                }
            }
        }
        stands.sort(Comparator.comparingInt((Stand stand) -> stand.farmland ? 1 : 0)
                .thenComparingLong(stand -> distanceSquared(from, stand.feet))
                .thenComparing(stand -> stand.feet));
        return stands.stream().map(Stand::feet).toList();
    }

    private static boolean canStand(BlockCoord feet, SafetyView safety) {
        if (safety.cellAt(feet) != Cell.PASSABLE) {
            return false;
        }
        if (safety.cellAt(offset(feet, 0, 1, 0)) != Cell.PASSABLE) {
            return false;
        }
        BlockCoord floor = offset(feet, 0, -1, 0);
        if (safety.cellAt(floor) != Cell.SOLID) {
            return false;
        }
        return ringSafe(feet, safety) && ringSafe(floor, safety);
    }

    private static boolean ringSafe(BlockCoord center, SafetyView safety) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Cell neighbor = safety.cellAt(offset(center, dx, 0, dz));
                if (neighbor == Cell.LAVA || neighbor == Cell.FIRE || neighbor == Cell.HAZARD
                        || neighbor == Cell.FALLING) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean withinReach(BlockCoord feet, BlockCoord target) {
        double dx = (double) feet.x() - target.x();
        double dy = (double) feet.y() + EYE_HEIGHT - ((double) target.y() + 0.5);
        double dz = (double) feet.z() - target.z();
        return dx * dx + dy * dy + dz * dz <= MAX_REACH_SQUARED;
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

    private record Stand(BlockCoord feet, boolean farmland) {
    }
}
