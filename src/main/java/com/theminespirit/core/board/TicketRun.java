package com.theminespirit.core.board;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.task.SpiritReading;
import org.jspecify.annotations.Nullable;

public enum TicketRun {
    RUNNING,
    SUCCEEDED,
    FAILED,
    DISPLACED,
    RESUME;

    public static TicketRun decide(
            long issuedGeneration, long currentGeneration, @Nullable RunStatus status, boolean externalControl) {
        boolean known = issuedGeneration != SpiritReading.UNKNOWN_GENERATION;
        if (known && issuedGeneration != currentGeneration) {
            return status == RunStatus.RUNNING || externalControl ? DISPLACED : RESUME;
        }
        if (!known && status == RunStatus.RUNNING && !externalControl) {
            return RESUME;
        }
        if (status == RunStatus.SUCCEEDED) {
            return SUCCEEDED;
        }
        if (status == RunStatus.FAILED) {
            return FAILED;
        }
        if (known && status == RunStatus.CANCELLED && !externalControl) {
            return RESUME;
        }
        return RUNNING;
    }
}
