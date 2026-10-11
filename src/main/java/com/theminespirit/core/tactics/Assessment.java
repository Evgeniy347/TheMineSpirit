package com.theminespirit.core.tactics;

public record Assessment(EnemyMetrics enemies, double winMargin, int escapeRoutes, CombatMode mode,
        boolean initiates, double zoneDistance, double leashLeft, double engageLeft, double speedRatio,
        TacticalState state, long ticksInState, long ticksSinceEnemySeen) {
    public static final double UNBOUNDED = 1000.0;

    public Assessment withEscapeRoutes(int routes) {
        return new Assessment(enemies, winMargin, routes, mode, initiates, zoneDistance, leashLeft, engageLeft,
                speedRatio, state, ticksInState, ticksSinceEnemySeen);
    }
}
