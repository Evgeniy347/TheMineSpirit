package com.theminespirit.core.task;

import com.theminespirit.core.engine.RunProgress;
import com.theminespirit.core.engine.RunStatus;

public record SpiritReading(boolean present, boolean alive, RunStatus status, RunProgress progress,
        String failureReason, RunProgress failedAt, long generation) {
    public static final long UNKNOWN_GENERATION = 0;

    private static final SpiritReading ABSENT = new SpiritReading(false, false, null, null, null, null,
            UNKNOWN_GENERATION);
    private static final SpiritReading DEAD = new SpiritReading(true, false, null, null, null, null,
            UNKNOWN_GENERATION);

    public static SpiritReading absent() {
        return ABSENT;
    }

    public static SpiritReading dead() {
        return DEAD;
    }

    public static SpiritReading living(RunStatus status, RunProgress progress, String failureReason) {
        return new SpiritReading(true, true, status, progress, failureReason, null, UNKNOWN_GENERATION);
    }

    public static SpiritReading failed(String failureReason, RunProgress failedAt) {
        return new SpiritReading(true, true, RunStatus.FAILED, null, failureReason, failedAt, UNKNOWN_GENERATION);
    }

    public SpiritReading withGeneration(long taskGeneration) {
        return new SpiritReading(present, alive, status, progress, failureReason, failedAt, taskGeneration);
    }
}
