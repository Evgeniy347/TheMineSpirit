package com.theminespirit.core.rest;

import java.time.Duration;

public final class RestTuning {
    public static final int HTTP_THREADS = 8;
    public static final int MAX_BODY_BYTES = 1024 * 1024;
    public static final Duration SERVER_CALL_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration OBSERVE_TIMEOUT = Duration.ofSeconds(15);

    private RestTuning() {
    }
}
