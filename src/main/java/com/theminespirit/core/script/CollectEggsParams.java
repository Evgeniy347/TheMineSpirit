package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record CollectEggsParams(int radius) {
    public CollectEggsParams {
        if (radius < CatalogLimits.HERD_RADIUS_MIN || radius > CatalogLimits.HERD_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.HERD_RADIUS_MIN + ", " + CatalogLimits.HERD_RADIUS_MAX
                    + "]: яйца собирают только в своём загоне");
        }
    }

    public static CollectEggsParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new CollectEggsParams(ScriptArgs.requireInt(params, "radius"));
    }
}
