package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record TillParams(int radius, int count) {
    public TillParams {
        if (radius < CatalogLimits.FARM_RADIUS_MIN || radius > CatalogLimits.FARM_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.FARM_RADIUS_MIN + ", " + CatalogLimits.FARM_RADIUS_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число клеток " + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static TillParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new TillParams(
                ScriptArgs.requireInt(params, "radius"),
                ScriptArgs.requireInt(params, "count"));
    }
}
