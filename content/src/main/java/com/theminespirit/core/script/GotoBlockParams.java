package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record GotoBlockParams(String block, int radius, int count) {
    public GotoBlockParams {
        if (block == null || block.isBlank()) {
            throw new IllegalArgumentException("Не задан блок для goto_block: без него скрипт не знает, к чему идти");
        }
        if (radius < CatalogLimits.NAV_RADIUS_MIN || radius > CatalogLimits.NAV_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.NAV_RADIUS_MIN + ", " + CatalogLimits.NAV_RADIUS_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число блоков " + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static GotoBlockParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new GotoBlockParams(
                ScriptArgs.requireString(params, "block"),
                ScriptArgs.requireInt(params, "radius"),
                ScriptArgs.requireInt(params, "count"));
    }
}
