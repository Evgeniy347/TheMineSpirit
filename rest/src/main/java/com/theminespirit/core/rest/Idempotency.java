package com.theminespirit.core.rest;

public interface Idempotency {
    IdempotencyCache.Result check(String requestId, String fingerprint);

    void remember(String requestId, String fingerprint, String response);

    void release(String requestId);
}
