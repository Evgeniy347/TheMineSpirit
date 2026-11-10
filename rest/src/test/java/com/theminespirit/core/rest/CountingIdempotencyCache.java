package com.theminespirit.core.rest;

import java.util.concurrent.CountDownLatch;

final class CountingIdempotencyCache extends IdempotencyCache {
    private final CountDownLatch checks;

    CountingIdempotencyCache(int expectedChecks) {
        checks = new CountDownLatch(expectedChecks);
    }

    @Override
    public synchronized Result check(String requestId, String fingerprint) {
        checks.countDown();
        return super.check(requestId, fingerprint);
    }

    void awaitChecks(String message) {
        Threads.await(checks, message);
    }
}
