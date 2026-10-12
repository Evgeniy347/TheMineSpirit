package com.theminespirit.core.task;

import com.theminespirit.core.engine.RunProgress;
import com.theminespirit.core.engine.RunStatus;

public record SpiritReading(boolean present, boolean alive, RunStatus status, RunProgress progress,
        String failureReason) {
    private static final SpiritReading ABSENT = new SpiritReading(false, false, null, null, null);
    private static final SpiritReading DEAD = new SpiritReading(true, false, null, null, null);

    public static SpiritReading absent() {
        return ABSENT;
    }

    public static SpiritReading dead() {
        return DEAD;
    }

    public static SpiritReading living(RunStatus status, RunProgress progress, String failureReason) {
        return new SpiritReading(true, true, status, progress, failureReason);
    }
}
