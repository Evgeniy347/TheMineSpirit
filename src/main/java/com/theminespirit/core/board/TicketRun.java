package com.theminespirit.core.board;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.task.SpiritReading;
import org.jspecify.annotations.Nullable;

/**
 * Итог задания доски по задаче духа: закрывает задание только задача, которую доска выдала по нему (её поколение).
 * Чужая задача (бой, рефлекс силы, дверь, REST) вытесняет задание временно: оно остаётся за этим духом, другие его
 * не забирают, а когда дух свободен — доска перезапускает его план. Итог чужой задачи заданию не приписывается.
 */
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
