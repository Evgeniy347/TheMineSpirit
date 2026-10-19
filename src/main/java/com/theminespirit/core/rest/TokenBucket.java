package com.theminespirit.core.rest;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TokenBucket {
    public static final double CAPACITY = 40;
    public static final double REFILL_PER_SECOND = 20;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int MAX_KEYS = 256;

    private record Bucket(double tokens, long updatedAtNanos) {
    }

    private final Map<String, Bucket> buckets = new LinkedHashMap<>();

    public synchronized boolean tryAcquire(String key, long nowNanos) {
        Bucket current = buckets.get(key);
        if (current == null) {
            if (buckets.size() >= MAX_KEYS) {
                String oldest = buckets.keySet().iterator().next();
                buckets.remove(oldest);
            }
            current = new Bucket(CAPACITY, nowNanos);
        }
        double refilled = Math.min(CAPACITY,
                current.tokens() + (nowNanos - current.updatedAtNanos()) / (double) NANOS_PER_SECOND * REFILL_PER_SECOND);
        if (refilled < 1) {
            buckets.put(key, new Bucket(refilled, nowNanos));
            return false;
        }
        buckets.put(key, new Bucket(refilled - 1, nowNanos));
        return true;
    }

    public synchronized long retryAfterSeconds(String key, long nowNanos) {
        Bucket current = buckets.get(key);
        if (current == null) {
            return 0;
        }
        double refilled = Math.min(CAPACITY,
                current.tokens() + (nowNanos - current.updatedAtNanos()) / (double) NANOS_PER_SECOND * REFILL_PER_SECOND);
        if (refilled >= 1) {
            return 0;
        }
        double missing = 1 - refilled;
        long seconds = (long) Math.ceil(missing / REFILL_PER_SECOND);
        return Math.max(seconds, 1);
    }

}
