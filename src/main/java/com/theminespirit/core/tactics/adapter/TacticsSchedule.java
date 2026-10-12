package com.theminespirit.core.tactics.adapter;

public final class TacticsSchedule {
    public static final int HURT_MIN_GAP_TICKS = 5;

    private static final long NEVER = Long.MIN_VALUE / 2;

    private final int intervalTicks;
    private long lastEvaluation = NEVER;
    private long lastEvent = NEVER;

    public TacticsSchedule(int intervalTicks) {
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("TacticsSchedule.intervalTicks = " + intervalTicks
                    + ": интервал оценки не короче тика; возьмите evalIntervalTicks из настроек адаптера");
        }
        this.intervalTicks = intervalTicks;
    }

    public boolean due(long now, boolean hurt, boolean forced) {
        if (forced || now - lastEvaluation >= intervalTicks) {
            return true;
        }
        return hurt && now - lastEvent >= HURT_MIN_GAP_TICKS;
    }

    public void evaluated(long now, boolean byEvent) {
        lastEvaluation = now;
        if (byEvent) {
            lastEvent = now;
        }
    }

    public boolean everEvaluated() {
        return lastEvaluation != NEVER;
    }
}
