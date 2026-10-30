package com.theminespirit.core.policy;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ReflexTimeout {
    private @Nullable String key;
    private int ticks;

    public boolean expired(PolicyId policy, String key, int limit, boolean airStillUnderWater) {
        Objects.requireNonNull(policy, "Политика рефлекса не может быть null: без неё неизвестно, что считать");
        Objects.requireNonNull(key, "Ключ рефлекса не может быть null: без него тики разных рефлексов смешаются");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Ключ рефлекса пуст: строка «политика:действие» обязана быть читаемой");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Предел рефлекса " + limit + " тиков не положителен: рефлекс без срока");
        }
        boolean hold = policy == PolicyId.AIR && airStillUnderWater;
        if (hold) {
            this.key = key;
            ticks = 0;
            return false;
        }
        ticks = key.equals(this.key) ? ticks + 1 : 1;
        this.key = key;
        return ticks >= limit;
    }

    public void reset() {
        key = null;
        ticks = 0;
    }
}
