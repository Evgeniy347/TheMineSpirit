package com.theminespirit.core.rest;

import java.util.Map;
import java.util.Objects;

public record RestResponse(int status, Map<String, String> headers, String body, SseStream stream) {
    public RestResponse {
        Objects.requireNonNull(headers, "headers не может быть null: пустой набор задают пустой картой");
        Objects.requireNonNull(body, "body не может быть null: тело ответа JSON, пустого тела нет");
        headers = Map.copyOf(headers);
    }

    public RestResponse(int status, Map<String, String> headers, String body) {
        this(status, headers, body, null);
    }

    public static RestResponse json(int status, String body) {
        return new RestResponse(status, Map.of(), body);
    }

    public static RestResponse ok(String body) {
        return json(200, body);
    }

    public static RestResponse stream(SseStream stream) {
        Objects.requireNonNull(stream, "stream не может быть null: стриминговый ответ обязан иметь источник кадров");
        return new RestResponse(200, Map.of("Content-Type", "text/event-stream"), "", stream);
    }
}
