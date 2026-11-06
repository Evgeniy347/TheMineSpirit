package com.theminespirit.core.tactics;

import java.util.Optional;

public final class Leash {
    private Leash() {}

    public static LeashResult check(ModeParams params, double zoneRadius, Point anchor, Point pos, Point target) {
        if (!params.leash()) {
            return new LeashResult.Within(Assessment.UNBOUNDED);
        }
        double farthest = Math.max(anchor.distanceTo(target), anchor.distanceTo(pos));
        double left = params.pursuit(zoneRadius) - farthest;
        return left >= 0 ? new LeashResult.Within(left) : new LeashResult.Exceeded(-left);
    }

    public static Optional<Point> returnTarget(ModeParams params, Optional<Point> anchor, Point pos, double tolerance) {
        return anchor.filter(point -> params.returnToAnchor() && point.distanceTo(pos) > tolerance);
    }
}
