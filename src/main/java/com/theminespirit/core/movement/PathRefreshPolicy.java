package com.theminespirit.core.movement;

public final class PathRefreshPolicy {
    public static final double TARGET_MOVE_TOLERANCE = 1.0;
    public static final int RETRY_COOLDOWN_CALLS = 60;

    private final double moveToleranceSq;
    private double targetX;
    private double targetY;
    private double targetZ;
    private boolean primed;
    private boolean pathWasActive;
    private int retryCooldown;

    public PathRefreshPolicy() {
        this(TARGET_MOVE_TOLERANCE);
    }

    public PathRefreshPolicy(double toleranceBlocks) {
        if (toleranceBlocks < 0) {
            throw new IllegalArgumentException("Допуск смещения цели " + toleranceBlocks
                    + " отрицателен: отрицательный допуск запрещает любой путь и дух встанет");
        }
        this.moveToleranceSq = toleranceBlocks * toleranceBlocks;
    }

    public boolean shouldRefresh(double x, double y, double z, boolean pathFinished) {
        if (!primed || movedBeyondTolerance(x, y, z)) {
            remember(x, y, z);
            pathWasActive = false;
            retryCooldown = 0;
            return true;
        }
        if (!pathFinished) {
            pathWasActive = true;
            retryCooldown = 0;
            return false;
        }
        if (pathWasActive) {
            pathWasActive = false;
            retryCooldown = RETRY_COOLDOWN_CALLS;
            return true;
        }
        if (retryCooldown > 0) {
            retryCooldown--;
            return false;
        }
        retryCooldown = RETRY_COOLDOWN_CALLS;
        return true;
    }

    private boolean movedBeyondTolerance(double x, double y, double z) {
        double dx = x - targetX;
        double dy = y - targetY;
        double dz = z - targetZ;
        return dx * dx + dy * dy + dz * dz > moveToleranceSq;
    }

    private void remember(double x, double y, double z) {
        primed = true;
        targetX = x;
        targetY = y;
        targetZ = z;
    }
}