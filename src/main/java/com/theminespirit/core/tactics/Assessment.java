package com.theminespirit.core.tactics;

public record Assessment(
        EnemyMetrics enemies,
        double winMargin,
        int escapeRoutes,
        double escapeQuality,
        CombatMode mode,
        boolean initiates,
        double zoneDistance,
        double leashLeft,
        double engageLeft,
        double speedRatio,
        TacticalState state,
        long ticksInState,
        long ticksSinceEnemySeen) {
    public static final double UNBOUNDED = 1000.0;

    public Assessment {
        FactChecks.range("Assessment.escapeQuality", escapeQuality, 0, 1);
    }

    public Assessment withEscapeRoutes(int routes) {
        return new Assessment(
                enemies,
                winMargin,
                routes,
                escapeQuality,
                mode,
                initiates,
                zoneDistance,
                leashLeft,
                engageLeft,
                speedRatio,
                state,
                ticksInState,
                ticksSinceEnemySeen);
    }

    public Assessment withEscapeQuality(double quality) {
        return new Assessment(
                enemies,
                winMargin,
                escapeRoutes,
                quality,
                mode,
                initiates,
                zoneDistance,
                leashLeft,
                engageLeft,
                speedRatio,
                state,
                ticksInState,
                ticksSinceEnemySeen);
    }
}
