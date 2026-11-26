package com.theminespirit.neoforge.rest;

import com.theminespirit.core.engine.RunStatus;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class ExternalTaskStart {
    private ExternalTaskStart() {}

    static Optional<String> refusal(@Nullable RunStatus status, boolean boardAssigned) {
        if (status == RunStatus.RUNNING) {
            return Optional.of("Дух уже выполняет задачу, поставленную вне REST: отмените её или дождитесь завершения");
        }
        if (boardAssigned) {
            return Optional.of(
                    "Дух закреплён за заданием доски: сначала отмените билет, внешняя задача не вытесняет граф");
        }
        return Optional.empty();
    }
}
