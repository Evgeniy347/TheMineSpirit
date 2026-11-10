package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PatrolPoints {
    public static final int NO_GROUND = Integer.MIN_VALUE;
    private static final int RADIUS = 4;
    private static final int MAX_RISE = 3;

    public interface Ground {
        int feet(int x, int z);

        boolean free(BlockCoord feet);
    }

    private PatrolPoints() {}

    public static List<BlockCoord> around(BlockCoord center, Ground ground) {
        Objects.requireNonNull(center, "Центр патруля не может быть null: без него нет точек");
        Objects.requireNonNull(ground, "Проверка стоянок не может быть null: иначе точки выдумываются");
        List<BlockCoord> points = new ArrayList<>(5);
        for (BlockCoord column : columns(center)) {
            int feet = ground.feet(column.x(), column.z());
            if (feet == NO_GROUND || Math.abs(feet - center.y()) > MAX_RISE) {
                continue;
            }
            BlockCoord cell = new BlockCoord(column.x(), feet, column.z());
            if (!ground.free(cell) || points.contains(cell)) {
                continue;
            }
            points.add(cell);
        }
        return List.copyOf(points);
    }

    private static List<BlockCoord> columns(BlockCoord center) {
        return List.of(
                center,
                new BlockCoord(center.x() + RADIUS, center.y(), center.z()),
                new BlockCoord(center.x(), center.y(), center.z() + RADIUS),
                new BlockCoord(center.x() - RADIUS, center.y(), center.z()),
                new BlockCoord(center.x(), center.y(), center.z() - RADIUS));
    }
}
