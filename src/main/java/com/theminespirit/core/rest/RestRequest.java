package com.theminespirit.core.rest;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public final class RestRequest {
    private final String method;
    private final String path;
    private final Map<String, String> query;
    private final Map<String, String> headers;
    private final byte[] body;

    public RestRequest(
            String method, String path, Map<String, String> query, Map<String, String> headers, byte[] body) {
        Objects.requireNonNull(method, "method не может быть null: маршрут выбирается по методу и пути");
        Objects.requireNonNull(path, "path не может быть null: маршрут выбирается по методу и пути");
        Objects.requireNonNull(query, "query не может быть null: пустой запрос задают пустой картой");
        Objects.requireNonNull(headers, "headers не может быть null: пустой набор задают пустой картой");
        Objects.requireNonNull(body, "body не может быть null: пустое тело задают пустым массивом");
        query = Map.copyOf(query);
        headers = Map.copyOf(headers);
        this.method = method;
        this.path = path;
        this.query = query;
        this.headers = headers;
        this.body = body;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public Map<String, String> query() {
        return query;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public byte[] body() {
        return body;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RestRequest other
                && Objects.equals(method, other.method)
                && Objects.equals(path, other.path)
                && Objects.equals(query, other.query)
                && Objects.equals(headers, other.headers)
                && Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(method, path, query, headers) + Arrays.hashCode(body);
    }

    @Override
    public String toString() {
        return "RestRequest[" + "method=" + method + ", " + "path=" + path + ", " + "query=" + query + ", " + "headers="
                + headers + ", " + "body=" + Arrays.toString(body) + "]";
    }
}
