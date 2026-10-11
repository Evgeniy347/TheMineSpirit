package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record FollowParams(String target, int radius) {
    public static final int DEFAULT_RADIUS = 3;

    public FollowParams {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException(
                    "Не задана цель follow: без цели скрипт не знает, за кем следовать");
        }
        if (radius < CatalogLimits.FOLLOW_RADIUS_MIN || radius > CatalogLimits.FOLLOW_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус удержания " + radius + " вне диапазона [" +
                    CatalogLimits.FOLLOW_RADIUS_MIN + ", " + CatalogLimits.FOLLOW_RADIUS_MAX +
                    "]: значение должно лежать в этом промежутке");
        }
    }

    public static FollowParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new FollowParams(
                ScriptArgs.requireString(params, "target"),
                ScriptArgs.optionalInt(params, "radius", DEFAULT_RADIUS));
    }
}
