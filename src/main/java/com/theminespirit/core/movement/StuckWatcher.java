package com.theminespirit.core.movement;

public final class StuckWatcher {
    private final double minMovementSq;
    private final int stuckTicks;
    private double lastX;
    private double lastY;
    private double lastZ;
    private int stillTicks;
    private boolean primed;

    public StuckWatcher(double minMovement, int stuckTicks) {
        if (minMovement <= 0) {
            throw new IllegalArgumentException("Порог движения " + minMovement
                    + " не положителен: нулевой порог считает застреванием любой шум позиции");
        }
        if (stuckTicks < 1) {
            throw new IllegalArgumentException("Число тиков застревания " + stuckTicks
                    + " меньше единицы: детект сработал бы до первого наблюдения");
        }
        this.minMovementSq = minMovement * minMovement;
        this.stuckTicks = stuckTicks;
    }

    public boolean observe(double x, double y, double z) {
        if (!primed) {
            primed = true;
            remember(x, y, z);
            return false;
        }
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        if (dx * dx + dy * dy + dz * dz < minMovementSq) {
            stillTicks++;
        } else {
            stillTicks = 0;
            remember(x, y, z);
        }
        return stillTicks >= stuckTicks;
    }

    private void remember(double x, double y, double z) {
        lastX = x;
        lastY = y;
        lastZ = z;
    }
}
