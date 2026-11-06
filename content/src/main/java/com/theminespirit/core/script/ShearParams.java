package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record ShearParams(int radius, int count) {
    public ShearParams {
        if (radius < CatalogLimits.HERD_RADIUS_MIN || radius > CatalogLimits.HERD_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.HERD_RADIUS_MIN + ", " + CatalogLimits.HERD_RADIUS_MAX
                    + "]: шире загона стричь некого");
        }
        if (count < CatalogLimits.HERD_COUNT_MIN || count > CatalogLimits.HERD_COUNT_MAX) {
            throw new IllegalArgumentException("Число овец " + count + " вне диапазона ["
                    + CatalogLimits.HERD_COUNT_MIN + ", " + CatalogLimits.HERD_COUNT_MAX
                    + "]: за один запуск стригут ограниченную отару");
        }
    }

    public static ShearParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new ShearParams(ScriptArgs.requireInt(params, "radius"), ScriptArgs.requireInt(params, "count"));
    }
}
