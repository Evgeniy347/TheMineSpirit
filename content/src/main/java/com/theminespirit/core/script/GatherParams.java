package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record GatherParams(String block, int count, int radius) {
    public GatherParams {
        if (block == null || block.isBlank()) {
            throw new IllegalArgumentException("Не задан блок для gather: без него скрипт не знает, что собирать");
        }
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число блоков " + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
        if (radius < CatalogLimits.HARVEST_RADIUS_MIN || radius > CatalogLimits.HARVEST_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.HARVEST_RADIUS_MIN + ", " + CatalogLimits.HARVEST_RADIUS_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static GatherParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new GatherParams(
                ScriptArgs.requireString(params, "block"),
                ScriptArgs.requireInt(params, "count"),
                ScriptArgs.requireInt(params, "radius"));
    }
}
