package com.theminespirit.core.tactics;

import java.util.concurrent.atomic.AtomicLong;

public final class RuleCounter {
    private final AtomicLong checks = new AtomicLong();

    void check() {
        checks.incrementAndGet();
    }

    public long checks() {
        return checks.get();
    }
}
