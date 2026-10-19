package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record WanderParams(int radius, int durationTicks) {
    public WanderParams {
        if (radius < CatalogLimits.NAV_RADIUS_MIN || radius > CatalogLimits.NAV_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус блуждания " + radius + " вне диапазона ["
                    + CatalogLimits.NAV_RADIUS_MIN + ", " + CatalogLimits.NAV_RADIUS_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
        if (durationTicks < CatalogLimits.NAV_WANDER_DURATION_MIN
                || durationTicks > CatalogLimits.NAV_WANDER_DURATION_MAX) {
            throw new IllegalArgumentException("Длительность блуждания " + durationTicks + " вне диапазона ["
                    + CatalogLimits.NAV_WANDER_DURATION_MIN + ", " + CatalogLimits.NAV_WANDER_DURATION_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static WanderParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new WanderParams(
                ScriptArgs.requireInt(params, "radius"), ScriptArgs.requireInt(params, "duration_ticks"));
    }
}
