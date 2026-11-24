package com.theminespirit.core.ui;

public record RefreshClock(int lastTick, boolean seen) {
    public static final int TICKS_PER_SECOND = 20;
    public static final RefreshClock NEVER = new RefreshClock(-1, false);

    public RefreshClock {
        if (seen && lastTick < 0) {
            throw new IllegalArgumentException(
                    "Тик ответа " + lastTick + " отрицателен при seen=true: индикатору нечего показывать");
        }
    }

    public static RefreshClock at(int tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("Тик ответа " + tick + " отрицателен: время идёт вперёд");
        }
        return new RefreshClock(tick, true);
    }

    public int seconds(int nowTick) {
        if (!seen) {
            return -1;
        }
        int age = Math.max(0, nowTick - lastTick);
        return age / TICKS_PER_SECOND;
    }
}
