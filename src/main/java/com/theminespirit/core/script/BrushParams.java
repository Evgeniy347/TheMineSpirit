package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record BrushParams(int radius, int count) {
    public BrushParams {
        if (radius < CatalogLimits.HERD_RADIUS_MIN || radius > CatalogLimits.HERD_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.HERD_RADIUS_MIN + ", " + CatalogLimits.HERD_RADIUS_MAX
                    + "]: дальше броненосца не найти");
        }
        if (count < CatalogLimits.HERD_COUNT_MIN || count > CatalogLimits.HERD_COUNT_MAX) {
            throw new IllegalArgumentException("Число щетин " + count + " вне диапазона ["
                    + CatalogLimits.HERD_COUNT_MIN + ", " + CatalogLimits.HERD_COUNT_MAX
                    + "]: за один запуск чистят ограниченное число броненосцев");
        }
    }

    public static BrushParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new BrushParams(ScriptArgs.requireInt(params, "radius"), ScriptArgs.requireInt(params, "count"));
    }
}
