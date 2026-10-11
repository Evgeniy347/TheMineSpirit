package com.theminespirit.core.engine;

final class EngineCounters {
    static long snapshotVisits;
    static long executorCreations;

    private EngineCounters() {
    }

    static void reset() {
        snapshotVisits = 0;
        executorCreations = 0;
    }
}
