package com.theminespirit.core.pickup;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class DropRoute {
    private DropRoute() {}

    public static Optional<DropRef> next(
            double fromX, double fromY, double fromZ, List<DropRef> drops, Set<String> skipped) {
        Objects.requireNonNull(drops, "drops не может быть null: пустой список кучек задаётся пустым списком");
        Objects.requireNonNull(skipped, "skipped не может быть null: пустое множество задаётся пустым множеством");
        DropRef best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (DropRef drop : drops) {
            if (!drop.pickable() || skipped.contains(drop.id())) {
                continue;
            }
            double distance = distanceSquared(fromX, fromY, fromZ, drop);
            if (best == null
                    || distance < bestDistance
                    || (distance == bestDistance && drop.id().compareTo(best.id()) < 0)) {
                best = drop;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static double distanceSquared(double fromX, double fromY, double fromZ, DropRef drop) {
        double dx = drop.x() - fromX;
        double dy = drop.y() - fromY;
        double dz = drop.z() - fromZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
