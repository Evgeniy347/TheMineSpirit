package com.theminespirit.core.tactics;

public record RallyTuning(int rallyRadius, int callRadius, int callMinEnemies, int callIntervalTicks, int helpRadius) {
    public static final int MAX_RADIUS = 256;
    public static final int MAX_MIN_ENEMIES = Situation.MAX_ENEMIES;
    public static final int MAX_CALL_INTERVAL_TICKS = 6000;

    public RallyTuning {
        FactChecks.range("RallyTuning.rallyRadius", rallyRadius, 1, MAX_RADIUS);
        FactChecks.range("RallyTuning.callRadius", callRadius, 1, MAX_RADIUS);
        FactChecks.range("RallyTuning.callMinEnemies", callMinEnemies, 1, MAX_MIN_ENEMIES);
        FactChecks.range("RallyTuning.callIntervalTicks", callIntervalTicks, 1, MAX_CALL_INTERVAL_TICKS);
        FactChecks.range("RallyTuning.helpRadius", helpRadius, 1, MAX_RADIUS);
    }
}
