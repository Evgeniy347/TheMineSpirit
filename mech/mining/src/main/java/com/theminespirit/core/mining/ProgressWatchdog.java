package com.theminespirit.core.mining;

public final class ProgressWatchdog {
    private final int limitTicks;
    private int idleTicks;

    public ProgressWatchdog(int limitTicks) {
        if (limitTicks < 1) {
            throw new IllegalArgumentException(
                    "Предел застоя " + limitTicks + " меньше единицы: сторож сработал бы до первого наблюдения");
        }
        this.limitTicks = limitTicks;
    }

    public void tick() {
        idleTicks++;
    }

    public void progress() {
        idleTicks = 0;
    }

    public boolean stalled() {
        return idleTicks >= limitTicks;
    }

    public int idleTicks() {
        return idleTicks;
    }

    public int limitTicks() {
        return limitTicks;
    }
}
