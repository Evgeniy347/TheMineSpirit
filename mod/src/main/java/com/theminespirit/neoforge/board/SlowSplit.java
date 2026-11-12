package com.theminespirit.neoforge.board;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;

final class SlowSplit {
    static final long SLOW_MILLIS = 200;
    static final long LOG_TICKS = 1200;

    private final Map<String, Long> worst = new LinkedHashMap<>();
    private long loggedAt = -LOG_TICKS;

    void mark(String phase, long startedNanos) {
        long millis = (System.nanoTime() - startedNanos) / 1_000_000;
        if (millis >= SLOW_MILLIS) {
            worst.merge(phase, millis, Math::max);
        }
    }

    void report(long now, Logger logger) {
        if (worst.isEmpty() || now - loggedAt < LOG_TICKS) {
            return;
        }
        loggedAt = now;
        logger.warn("[board] медленный тик вне пересборки за {} тиков (худшее по фазам, мс): {}", LOG_TICKS, worst);
        worst.clear();
    }

    Map<String, Long> worst() {
        return Map.copyOf(worst);
    }
}
