package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ChopStands {
    public static final int FAR_RADIUS = 3;

    private static final int NEAR_RADIUS = 1;
    private static final int STAND_HEIGHT = 2;
    private static final int DROP_DEPTH = 3;
    private static final double EYE_HEIGHT = 1.5;
    private static final double MAX_LOG_DISTANCE = 4.0;

    @FunctionalInterface
    public interface View {
        boolean standable(BlockCoord feet);
    }

    private ChopStands() {}

    public static List<BlockCoord> around(Tree tree, View view) {
        return collect(tree, view, NEAR_RADIUS, 0, STAND_HEIGHT - 1);
    }

    public static List<BlockCoord> stands(Tree tree, View view) {
        List<BlockCoord> near = around(tree, view);
        if (!near.isEmpty()) {
            return near;
        }
        return collect(tree, view, FAR_RADIUS, -DROP_DEPTH, STAND_HEIGHT - 1);
    }

    private static List<BlockCoord> collect(Tree tree, View view, int radius, int minDy, int maxDy) {
        Objects.requireNonNull(tree, "Дерево не может быть null: без него неизвестно, где стоянка у ствола");
        Objects.requireNonNull(view, "Взгляд на мир не может быть null: по нему проверяется стоянка");
        Set<BlockCoord> logs = Set.copyOf(tree.logs());
        int baseY = tree.base().y();
        List<BlockCoord> stands = new ArrayList<>();
        for (BlockCoord log : tree.logs()) {
            if (log.y() != baseY) {
                continue;
            }
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    for (int dy = minDy; dy <= maxDy; dy++) {
                        BlockCoord feet = new BlockCoord(log.x() + dx, baseY + dy, log.z() + dz);
                        BlockCoord below = new BlockCoord(feet.x(), feet.y() - 1, feet.z());
                        if (logs.contains(feet)
                                || logs.contains(below)
                                || !view.standable(feet)
                                || !withinReach(feet, log)) {
                            continue;
                        }
                        stands.add(feet);
                    }
                }
            }
        }
        return List.copyOf(new LinkedHashSet<>(stands));
    }

    private static boolean withinReach(BlockCoord feet, BlockCoord log) {
        double dx = feet.x() - log.x();
        double dz = feet.z() - log.z();
        double dy = log.y() + 0.5 - (feet.y() + EYE_HEIGHT);
        return dx * dx + dz * dz + dy * dy <= MAX_LOG_DISTANCE * MAX_LOG_DISTANCE;
    }
}
