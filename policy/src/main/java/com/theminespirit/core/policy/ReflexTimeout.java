package com.theminespirit.core.policy;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ReflexTimeout {
    private @Nullable String key;
    private int ticks;

    public boolean expired(PolicyId policy, String key, int limit, boolean underwater, boolean moving) {
        Objects.requireNonNull(policy, "Политика рефлекса не может быть null: без неё неизвестно, что считать");
        Objects.requireNonNull(key, "Ключ рефлекса не может быть null: без него тики разных рефлексов смешаются");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Ключ рефлекса пуст: строка «политика:действие» обязана быть читаемой");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Предел рефлекса " + limit + " тиков не положителен: рефлекс без срока");
        }
        // lib: рефлекс воды и воздуха держится только живой нуждой (голова под водой) или движением к выходу.
        // lib: «дух в воде» без движения — не рефлекс, а ловушка: дух запирался в яме навсегда (10.11, -99,52,-417).
        boolean hold = (policy == PolicyId.AIR || policy == PolicyId.WATER) && (underwater || moving);
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
