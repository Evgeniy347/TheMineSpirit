package com.theminespirit.core.rest;

public record HealthSnapshot(
        long nowMillis, long lastTickMillis, long tick, long thresholdMillis, boolean debugPaused) {
    public HealthSnapshot {
        if (thresholdMillis <= 0) {
            throw new IllegalArgumentException(
                    "Порог зависания " + thresholdMillis + " мс должен быть больше нуля: иначе любой простой "
                            + "выглядит зависанием и сторож перезапустит живой сервер");
        }
    }

    public HangVerdict verdict() {
        return HangVerdict.decide(nowMillis, lastTickMillis, thresholdMillis, debugPaused);
    }

    public long ageMillis() {
        if (lastTickMillis <= 0 || nowMillis <= lastTickMillis) {
            return lastTickMillis <= 0 ? -1 : 0;
        }
        return nowMillis - lastTickMillis;
    }
}
