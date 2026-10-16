package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record LightAreaParams(int x, int y, int z, int radius) {
    public LightAreaParams {
        BuildingCoords.checkX(x);
        BuildingCoords.checkY(y);
        BuildingCoords.checkZ(z);
        if (radius < CatalogLimits.LIGHT_RADIUS_MIN || radius > CatalogLimits.LIGHT_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус освещения = " + radius + ": область обязана лежать в ["
                    + CatalogLimits.LIGHT_RADIUS_MIN + ", " + CatalogLimits.LIGHT_RADIUS_MAX + "]");
        }
    }

    public static LightAreaParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new LightAreaParams(
                ScriptArgs.requireInt(params, "x"),
                ScriptArgs.requireInt(params, "y"),
                ScriptArgs.requireInt(params, "z"),
                ScriptArgs.requireInt(params, "radius"));
    }
}
