package com.theminespirit.core.rest;

public enum HangVerdict {
    OK("ok"),
    HUNG("hung"),
    DEBUG_PAUSED("debug"),
    UNKNOWN("unknown");

    private final String json;

    HangVerdict(String json) {
        this.json = json;
    }

    public String json() {
        return json;
    }

    public static HangVerdict decide(long nowMillis, long lastTickMillis, long thresholdMillis, boolean debugPaused) {
        if (thresholdMillis <= 0) {
            throw new IllegalArgumentException(
                    "Порог зависания " + thresholdMillis + " мс должен быть больше нуля: иначе любой простой "
                            + "выглядит зависанием и сторож перезапустит живой сервер");
        }
        if (debugPaused) {
            return DEBUG_PAUSED;
        }
        if (lastTickMillis <= 0) {
            return UNKNOWN;
        }
        if (nowMillis <= lastTickMillis) {
            return OK;
        }
        return nowMillis - lastTickMillis >= thresholdMillis ? HUNG : OK;
    }
}
