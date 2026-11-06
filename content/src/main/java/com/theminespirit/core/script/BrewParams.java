package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record BrewParams(String item, int count, int radius) {
    public BrewParams {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Не задано зелье для brew: без него скрипт не знает, что варить");
        }
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число зелий " + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
        if (radius < CatalogLimits.STATION_RADIUS_MIN || radius > CatalogLimits.STATION_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска стойки " + radius + " вне диапазона ["
                    + CatalogLimits.STATION_RADIUS_MIN + ", " + CatalogLimits.STATION_RADIUS_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static BrewParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new BrewParams(
                ScriptArgs.requireString(params, "item"),
                ScriptArgs.requireInt(params, "count"),
                ScriptArgs.requireInt(params, "radius"));
    }
}
