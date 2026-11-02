package com.theminespirit.core.board;

import java.util.Objects;
import java.util.Optional;

public final class SourceFailureLog {
    private final ChangeSummary log;

    public SourceFailureLog(long window) {
        this.log = new ChangeSummary(window);
    }

    public Optional<ChangeSummary.Summary> observe(String source, String message, long now) {
        Objects.requireNonNull(source, "Источник не может быть null: по нему ищут упавшую часть доски");
        Objects.requireNonNull(message, "Ошибка не может быть null: по ней отличают новый сбой от повтора");
        return log.note("source:" + source, message, 1, now);
    }
}
