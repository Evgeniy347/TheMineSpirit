package com.theminespirit.neoforge.board;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class TickCache<T> {
    private final long ttlTicks;
    private @Nullable Object key;
    private long tick;
    private @Nullable T value;

    TickCache(long ttlTicks) {
        this.ttlTicks = ttlTicks;
    }

    T get(Object currentKey, long now, Supplier<T> compute) {
        T cached = value;
        if (cached != null && currentKey.equals(key) && fresh(now - tick, ttlTicks)) {
            return cached;
        }
        T computed = compute.get();
        key = currentKey;
        tick = now;
        value = computed;
        return computed;
    }

    static boolean fresh(long age, long ttlTicks) {
        return age >= 0 && age < ttlTicks;
    }
}
