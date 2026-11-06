package com.theminespirit.core.rest;

import java.util.Locale;
import java.util.Objects;

public record Route(String method, String pattern, RouteHandler handler) {
    public Route {
        Objects.requireNonNull(handler, "handler не может быть null: маршрут без обработчика ничего не отвечает");
        if (method == null || method.isEmpty() || !method.equals(method.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Метод маршрута '" + method + "' пуст или не в верхнем регистре: "
                    + "HTTP-методы сравниваются точно, пример — GET");
        }
        RoutePattern.parse(pattern);
    }
}
