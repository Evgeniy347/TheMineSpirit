package com.theminespirit.core.rest;

@FunctionalInterface
public interface HealthGateway {
    HealthSnapshot snapshot(long nowMillis);

    static HealthGateway unknown() {
        return nowMillis -> new HealthSnapshot(nowMillis, 0, 0, 60_000, false);
    }
}
