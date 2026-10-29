package com.theminespirit.core.board;

public final class ActivityCadence {
    public static final long RECONCILE_TICKS = 10;

    private ActivityCadence() {}

    public static boolean due(long sinceTick, long nowTick) {
        if (nowTick < sinceTick) {
            throw new IllegalArgumentException("Тик " + nowTick + " раньше " + sinceTick
                    + ": время сервера идёт вперёд, иначе занятие духа не обновится на доске");
        }
        return nowTick - sinceTick >= RECONCILE_TICKS;
    }
}
