package com.theminespirit.core.rest;

import java.util.concurrent.CountDownLatch;

final class CountingIdempotencyCache implements Idempotency {
    private final IdempotencyCache delegate = new IdempotencyCache();
    private final CountDownLatch checks;

    CountingIdempotencyCache(int expectedChecks) {
        checks = new CountDownLatch(expectedChecks);
    }

    @Override
    public IdempotencyCache.Result check(String requestId, String fingerprint) {
        checks.countDown();
        return delegate.check(requestId, fingerprint);
    }

    @Override
    public void remember(String requestId, String fingerprint, String response) {
        delegate.remember(requestId, fingerprint, response);
    }

    @Override
    public void release(String requestId) {
        delegate.release(requestId);
    }

    void awaitChecks(String message) {
        Threads.await(checks, message);
    }
}
