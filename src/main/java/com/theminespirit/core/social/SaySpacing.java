package com.theminespirit.core.social;

public final class SaySpacing {
    public static final long NEVER = Long.MIN_VALUE;

    private SaySpacing() {}

    public static boolean due(long lastTick, long now, int minInterval) {
        if (minInterval < 0) {
            throw new IllegalArgumentException("Интервал анти-спама " + minInterval + " меньше 0: "
                    + "отрицательный интервал разрешил бы говорить чаще, чем задано");
        }
        if (lastTick == NEVER) {
            return true;
        }
        if (now < lastTick) {
            throw new IllegalArgumentException("Время пошло назад: now=" + now + " меньше lastTick=" + lastTick
                    + ": тики не отматываются, а молчаливая ложь здесь разрешила бы спам");
        }
        return now - lastTick >= minInterval;
    }
}
