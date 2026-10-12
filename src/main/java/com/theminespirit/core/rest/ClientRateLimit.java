package com.theminespirit.core.rest;

import java.util.Map;
import java.util.Optional;

public final class ClientRateLimit {
    private final TokenBucket bucket = new TokenBucket();

    public Optional<RestResponse> reject(String clientKey, long nowNanos) {
        if (bucket.tryAcquire(clientKey, nowNanos)) {
            return Optional.empty();
        }
        long retryAfter = bucket.retryAfterSeconds(clientKey, nowNanos);
        ApiError error = new ApiError(ErrorCode.RATE_LIMITED,
                "Слишком много запросов с этого адреса: лимит " + (int) TokenBucket.CAPACITY + " подряд и "
                        + (int) TokenBucket.REFILL_PER_SECOND + " в секунду, повторите через " + retryAfter + " с",
                Map.of("retry_after_seconds", retryAfter));
        return Optional.of(new RestResponse(error.code().status(), Map.of("Retry-After", Long.toString(retryAfter)),
                ErrorJson.toJson(error)));
    }
}
