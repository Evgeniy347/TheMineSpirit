package com.theminespirit.core.observation;

import java.util.Objects;
import java.util.Optional;

public final class DebouncedState {
    private final long holdTicks;
    private String current = "";
    private String candidate = "";
    private long candidateSince;

    public DebouncedState(long holdTicks) {
        if (holdTicks < 0) {
            throw new IllegalArgumentException(
                    "Выдержка " + holdTicks + " отрицательна: состояние не может " + "смениться раньше, чем началось");
        }
        this.holdTicks = holdTicks;
    }

    public Optional<String> update(String next, long now) {
        Objects.requireNonNull(next, "Состояние не может быть null: без него нечего сравнивать");
        if (!next.equals(candidate)) {
            candidate = next;
            candidateSince = now;
        }
        if (next.equals(current)) {
            return Optional.empty();
        }
        if (!current.isEmpty() && now - candidateSince < holdTicks) {
            return Optional.empty();
        }
        current = next;
        return Optional.of(next);
    }

    public String current() {
        return current;
    }
}
