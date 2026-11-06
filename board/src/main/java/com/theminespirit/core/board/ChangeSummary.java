package com.theminespirit.core.board;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ChangeSummary {
    public record Summary(boolean changed, long count, long min, long max, long total) {}

    private final StateChangeLog states = new StateChangeLog();
    private final RepeatLog repeats;

    public ChangeSummary(long window) {
        this.repeats = new RepeatLog(window);
    }

    public Optional<Summary> note(String key, String state, long value, long now) {
        Objects.requireNonNull(key, "Ключ события не может быть null: без него не с чем сравнивать повтор");
        Objects.requireNonNull(state, "Состояние события не может быть null: по нему решают, печатать ли строку");
        if (states.changed(key, state)) {
            repeats.clear(key);
            repeats.note(key, value, now);
            return Optional.of(new Summary(true, 1, value, value, value));
        }
        return repeats.note(key, value, now)
                .map(summary -> new Summary(false, summary.count(), summary.min(), summary.max(), summary.total()));
    }

    public void clear(String key) {
        states.forget(key);
        repeats.clear(key);
    }

    public void retain(Set<String> keys) {
        states.retain(keys);
        repeats.retain(keys);
    }
}
