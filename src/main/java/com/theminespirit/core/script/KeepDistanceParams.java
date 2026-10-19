package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record KeepDistanceParams(String target, int distance) {
    public KeepDistanceParams {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException(
                    "Не задана цель keep_distance: без цели скрипт не знает, от кого держать дистанцию");
        }
        if (distance < CatalogLimits.NAV_KEEP_DISTANCE_MIN || distance > CatalogLimits.NAV_KEEP_DISTANCE_MAX) {
            throw new IllegalArgumentException("Дистанция " + distance + " вне диапазона ["
                    + CatalogLimits.NAV_KEEP_DISTANCE_MIN + ", " + CatalogLimits.NAV_KEEP_DISTANCE_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static KeepDistanceParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new KeepDistanceParams(
                ScriptArgs.requireString(params, "target"), ScriptArgs.requireInt(params, "distance"));
    }
}
