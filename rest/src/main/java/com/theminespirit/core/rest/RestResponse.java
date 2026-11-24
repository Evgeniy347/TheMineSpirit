package com.theminespirit.core.rest;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record RestResponse(
        int status,
        Map<String, String> headers,
        String body,
        @Nullable SseStream stream,
        BinaryBody binary) {
    public RestResponse {
        Objects.requireNonNull(headers, "headers не может быть null: пустой набор задают пустой картой");
        Objects.requireNonNull(body, "body не может быть null: тело ответа JSON, пустого тела нет");
        headers = Map.copyOf(headers);
        Objects.requireNonNull(binary, "Тело-картинка не может быть null: без картинки — BinaryBody.none()");
        if (stream != null && binary.size() > 0) {
            throw new IllegalArgumentException("Ответ не может быть сразу потоком и картинкой: один вид тела на ответ");
        }
    }

    public RestResponse(int status, Map<String, String> headers, String body) {
        this(status, headers, body, null, BinaryBody.none());
    }

    public RestResponse(int status, Map<String, String> headers, String body, @Nullable SseStream stream) {
        this(status, headers, body, stream, BinaryBody.none());
    }

    public static RestResponse json(int status, String body) {
        return new RestResponse(status, Map.of(), body);
    }

    public static RestResponse ok(String body) {
        return json(200, body);
    }

    public static RestResponse png(byte[] image) {
        Objects.requireNonNull(image, "Картинка не может быть null: пустой PNG не отдают");
        return new RestResponse(200, Map.of("Content-Type", "image/png"), "", null, BinaryBody.of(image));
    }

    public static RestResponse stream(SseStream stream) {
        Objects.requireNonNull(stream, "stream не может быть null: стриминговый ответ обязан иметь источник кадров");
        return new RestResponse(200, Map.of("Content-Type", "text/event-stream"), "", stream);
    }
}
