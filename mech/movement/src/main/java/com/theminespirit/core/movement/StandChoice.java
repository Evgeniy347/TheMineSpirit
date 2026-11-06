package com.theminespirit.core.movement;

import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class StandChoice {
    public static final double EYE_HEIGHT = 1.62;
    public static final double REACH_DISTANCE = 4.5;
    public static final int HORIZONTAL_SPREAD = 3;

    private StandChoice() {}

    public static List<BlockCoord> candidates(BlockCoord target) {
        Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, вокруг чего искать стоянку");
        List<BlockCoord> result = new ArrayList<>();
        for (int level = 1; level <= 2; level++) {
            for (int dx = -HORIZONTAL_SPREAD; dx <= HORIZONTAL_SPREAD; dx++) {
                for (int dz = -HORIZONTAL_SPREAD; dz <= HORIZONTAL_SPREAD; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    result.add(new BlockCoord(target.x() + dx, target.y() + level, target.z() + dz));
                }
            }
        }
        return List.copyOf(result);
    }

    public static Optional<BlockCoord> choose(BlockCoord target, BlockCoord spiritFeet, SafetyView view) {
        Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, к чему подходить");
        Objects.requireNonNull(spiritFeet, "Позиция духа не может быть null: без неё неизвестно, откуда идти");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему проверяется стоянка");
        double centerX = target.x() + 0.5;
        double centerY = target.y() + 0.5;
        double centerZ = target.z() + 0.5;
        Comparator<BlockCoord> cheapestFirst = Comparator.comparingInt(
                        (BlockCoord stand) -> MoveCost.walkTicks(spiritFeet, stand))
                .thenComparingDouble(stand -> distanceSquared(
                        spiritFeet.x() + 0.5,
                        spiritFeet.y() + EYE_HEIGHT,
                        spiritFeet.z() + 0.5,
                        stand.x() + 0.5,
                        stand.y() + EYE_HEIGHT,
                        stand.z() + 0.5))
                .thenComparing(Comparator.naturalOrder());
        Optional<BlockCoord> best = Optional.empty();
        for (BlockCoord stand : candidates(target)) {
            if (!withinReach(stand, centerX, centerY, centerZ)) {
                continue;
            }
            if (!MiningSafety.canStand(stand, view)) {
                continue;
            }
            if (best.isEmpty() || cheapestFirst.compare(stand, best.orElseThrow()) < 0) {
                best = Optional.of(stand);
            }
        }
        return best;
    }

    private static boolean withinReach(BlockCoord stand, double centerX, double centerY, double centerZ) {
        double eyeX = stand.x() + 0.5;
        double eyeY = stand.y() + EYE_HEIGHT;
        double eyeZ = stand.z() + 0.5;
        return distanceSquared(eyeX, eyeY, eyeZ, centerX, centerY, centerZ) <= REACH_DISTANCE * REACH_DISTANCE;
    }

    private static double distanceSquared(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }
}
