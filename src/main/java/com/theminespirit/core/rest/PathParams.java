package com.theminespirit.core.rest;

import java.util.Map;

public record PathParams(Map<String, String> values) {
    public PathParams {
        values = Map.copyOf(values);
    }

    public String get(String name) {
        String value = values.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Параметр пути '" + name + "' не объявлен в шаблоне маршрута: "
                    + "доступны " + values.keySet());
        }
        return value;
    }
}
