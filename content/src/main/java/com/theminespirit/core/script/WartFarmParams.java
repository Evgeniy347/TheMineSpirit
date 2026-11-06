package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record WartFarmParams(int count, int radius) {
    public WartFarmParams {
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число посадок " + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
        if (radius < CatalogLimits.STATION_RADIUS_MIN || radius > CatalogLimits.STATION_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус грядки " + radius + " вне диапазона ["
                    + CatalogLimits.STATION_RADIUS_MIN + ", " + CatalogLimits.STATION_RADIUS_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static WartFarmParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new WartFarmParams(ScriptArgs.requireInt(params, "count"), ScriptArgs.requireInt(params, "radius"));
    }
}
