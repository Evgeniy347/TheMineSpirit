package com.theminespirit.core.movement.api;

public final class PathReuse {
    private PathReuse() {}

    public static boolean keep(
            boolean hasTarget,
            int targetX,
            int targetY,
            int targetZ,
            int wantX,
            int wantY,
            int wantZ,
            boolean pathActive) {
        return hasTarget && pathActive && targetX == wantX && targetY == wantY && targetZ == wantZ;
    }

    public static boolean futile(int nodes, boolean canReach) {
        return nodes <= 1 && !canReach;
    }

    public static boolean waiting(long now, long until) {
        return now < until;
    }
}
