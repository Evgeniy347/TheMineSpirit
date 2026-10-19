package com.theminespirit.core.rest;

import java.util.Objects;

public final class SseFormat {
    private static final String KEEPALIVE = ": keepalive\n\n";

    private SseFormat() {}

    public static String frame(long id, String event, String dataJson) {
        Objects.requireNonNull(event, "event не может быть null: тип кадра обязателен для SSE");
        Objects.requireNonNull(dataJson, "dataJson не может быть null: данные кадра — одна строка JSON");
        if (dataJson.indexOf('\n') >= 0 || dataJson.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Данные SSE-кадра содержат перевод строки: data обязан быть одной "
                    + "строкой, переносы внутри JSON экранируются как \\n");
        }
        return "id: " + id + "\nevent: " + event + "\ndata: " + dataJson + "\n\n";
    }

    public static String keepalive() {
        return KEEPALIVE;
    }
}
