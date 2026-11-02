package com.theminespirit.core.observation;

public final class StateSignature {
    private static final int LOW_AIR = 100;
    private static final int CRITICAL_AIR = 50;

    private StateSignature() {}

    public static String air(int airSupply) {
        if (airSupply < 0) {
            throw new IllegalArgumentException("Запас воздуха " + airSupply + " отрицателен: такого не бывает");
        }
        if (airSupply < CRITICAL_AIR) {
            return "critical";
        }
        return airSupply < LOW_AIR ? "low" : "full";
    }

    public static String spirit(int airSupply, boolean inWater) {
        return inWater + "|" + air(airSupply);
    }
}
