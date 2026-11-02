package com.theminespirit.core.board;

import java.util.Objects;
import java.util.Optional;

public final class FailureRepeat {
    public record Note(boolean changed, long count, String reason) {}

    private final ChangeSummary log;

    public FailureRepeat(long window) {
        this.log = new ChangeSummary(window);
    }

    public Optional<Note> note(String key, String reason, long now) {
        Objects.requireNonNull(key, "Ключ задания не может быть null: по нему отличают провалы разных заданий");
        Objects.requireNonNull(reason, "Причина провала не может быть null: без неё строку не с чем сравнивать");
        return log.note(key, reason, 1, now).map(summary -> new Note(summary.changed(), summary.count(), reason));
    }
}
