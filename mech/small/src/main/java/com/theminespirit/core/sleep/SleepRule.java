package com.theminespirit.core.sleep;

import java.util.Objects;

public final class SleepRule {
    public static final long TICKS_PER_DAY = 24_000L;
    public static final double NIGHT_DAY_FRACTION = 0.52;

    private SleepRule() {}

    public static double dayFraction(long dayTime) {
        return Math.floorMod(dayTime, TICKS_PER_DAY) / (double) TICKS_PER_DAY;
    }

    public static boolean isNight(double dayFraction) {
        if (Double.isNaN(dayFraction) || dayFraction < 0.0 || dayFraction > 1.0) {
            throw new IllegalArgumentException("Доля суток = " + dayFraction + ": время суток обязано лежать в 0..1");
        }
        return dayFraction >= NIGHT_DAY_FRACTION;
    }

    public static boolean timeToSleep(SleepFacts facts) {
        Objects.requireNonNull(
                facts,
                "Факты сна не могут быть null: правило решает по ночи, опасности, "
                        + "границе поселения и приказу обороны");
        return facts.night() && !facts.danger() && facts.insideSettlement() && !facts.assignedDefence();
    }
}
