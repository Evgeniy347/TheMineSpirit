package com.theminespirit.core.tactics;

public record EnemyProfile(
        String type,
        double hp,
        double dps,
        double speed,
        boolean ranged,
        boolean explosive,
        boolean swims,
        boolean flies,
        int tier) {
    public static final double MAX_HP = 2000;
    public static final double MAX_DPS = 200;
    public static final double MAX_SPEED = 2;
    public static final int MAX_TIER = 5;

    public EnemyProfile {
        FactChecks.required("EnemyProfile.type", type);
        FactChecks.range("EnemyProfile.hp", hp, 1, MAX_HP);
        FactChecks.range("EnemyProfile.dps", dps, 0, MAX_DPS);
        FactChecks.range("EnemyProfile.speed", speed, 0.01, MAX_SPEED);
        FactChecks.range("EnemyProfile.tier", tier, 0, MAX_TIER);
    }
}
