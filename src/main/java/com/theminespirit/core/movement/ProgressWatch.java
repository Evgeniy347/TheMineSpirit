package com.theminespirit.core.movement;

public final class ProgressWatch {
    private final double minProgress;
    private final int stalledTicks;
    private boolean primed;
    private double best;
    private int stalled;

    public ProgressWatch(double minProgress, int stalledTicks) {
        if (minProgress <= 0) {
            throw new IllegalArgumentException("Порог прогресса " + minProgress
                    + " не положителен: любой шум расстояния считался бы приближением к цели");
        }
        if (stalledTicks < 1) {
            throw new IllegalArgumentException("Число тиков без прогресса " + stalledTicks
                    + " меньше единицы: застревание сработало бы до первого наблюдения");
        }
        this.minProgress = minProgress;
        this.stalledTicks = stalledTicks;
    }

    public boolean observe(double distance) {
        if (!primed) {
            primed = true;
            best = distance;
            return false;
        }
        if (distance < best - minProgress) {
            best = distance;
            stalled = 0;
            return false;
        }
        if (distance < best) {
            best = distance;
        }
        stalled++;
        return stalled >= stalledTicks;
    }
}
