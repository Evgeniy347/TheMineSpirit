package com.theminespirit.core.board;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RepeatLog {
    public record Summary(long count, long min, long max, long total, long since) {}

    private record Burst(long count, long min, long max, long total, long since) {}

    private final long window;
    private final Map<String, Burst> bursts = new HashMap<>();

    public RepeatLog(long window) {
        if (window <= 0) {
            throw new IllegalArgumentException("Окно сводки " + window + " должно быть положительным: иначе сводка "
                    + "печатается каждый тик и снова заливает лог");
        }
        this.window = window;
    }

    public Optional<Summary> note(String key, long value, long now) {
        Objects.requireNonNull(key, "Ключ повтора не может быть null: без него не с чем сравнивать событие");
        Burst burst = bursts.get(key);
        if (burst == null) {
            bursts.put(key, new Burst(1, value, value, value, now));
            return Optional.of(new Summary(1, value, value, value, now));
        }
        long count = burst.count() + 1;
        long min = Math.min(burst.min(), value);
        long max = Math.max(burst.max(), value);
        long total = burst.total() + value;
        if (now - burst.since() < window) {
            bursts.put(key, new Burst(count, min, max, total, burst.since()));
            return Optional.empty();
        }
        bursts.put(key, new Burst(0, value, value, value, now));
        return Optional.of(new Summary(count, min, max, total, burst.since()));
    }

    public void clear(String key) {
        bursts.remove(key);
    }

    public void retain(Set<String> keys) {
        bursts.keySet().retainAll(keys);
    }
}
