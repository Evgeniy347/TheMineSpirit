package com.theminespirit.core.rest;

import java.util.Map;
import java.util.Objects;

public record RestRequest(
        String method, String path, Map<String, String> query, Map<String, String> headers, byte[] body) {
    public RestRequest {
        Objects.requireNonNull(method, "method не может быть null: маршрут выбирается по методу и пути");
        Objects.requireNonNull(path, "path не может быть null: маршрут выбирается по методу и пути");
        Objects.requireNonNull(query, "query не может быть null: пустой запрос задают пустой картой");
        Objects.requireNonNull(headers, "headers не может быть null: пустой набор задают пустой картой");
        Objects.requireNonNull(body, "body не может быть null: пустое тело задают пустым массивом");
        query = Map.copyOf(query);
        headers = Map.copyOf(headers);
    }
}
