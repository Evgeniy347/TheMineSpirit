package com.theminespirit.core.board;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class BreakLogKeys {
    private final Map<String, Long> seen;

    private BreakLogKeys(Map<String, Long> seen) {
        this.seen = new HashMap<>(Objects.requireNonNull(seen, "Ключи журнала не могут быть null"));
    }

    public static BreakLogKeys empty() {
        return new BreakLogKeys(Map.of());
    }

    public static BreakLogKeys of(Map<String, Long> seen) {
        return new BreakLogKeys(seen);
    }

    public boolean recently(String key, long now, int dedupeTicks) {
        Objects.requireNonNull(key, "Ключ журнала не может быть null: без него не отличить повтор");
        Long previous = seen.get(key);
        return previous != null && now - previous >= 0 && now - previous < dedupeTicks;
    }

    public void remember(String key, long now, int maxKeys) {
        Objects.requireNonNull(key, "Ключ журнала не может быть null: без него не отличить повтор");
        if (maxKeys < 1) {
            throw new IllegalArgumentException("Предел ключей " + maxKeys + ": кэш обязан хранить хотя бы один ключ");
        }
        if (seen.size() > maxKeys) {
            seen.clear();
        }
        seen.put(key, now);
    }

    public Map<String, Long> snapshot() {
        return Map.copyOf(seen);
    }

    public int size() {
        return seen.size();
    }
}
