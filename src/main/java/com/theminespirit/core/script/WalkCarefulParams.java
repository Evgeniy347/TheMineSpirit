package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record WalkCarefulParams(int x, int y, int z) {
    public WalkCarefulParams {
        checkHorizontal("x", x);
        checkHorizontal("z", z);
        if (y < CatalogLimits.WORLD_Y_MIN || y > CatalogLimits.WORLD_Y_MAX) {
            throw new IllegalArgumentException("Координата y=" + y + " вне диапазона [" + CatalogLimits.WORLD_Y_MIN
                    + ", " + CatalogLimits.WORLD_Y_MAX + "]");
        }
    }

    public static WalkCarefulParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new WalkCarefulParams(
                ScriptArgs.requireInt(params, "x"),
                ScriptArgs.requireInt(params, "y"),
                ScriptArgs.requireInt(params, "z"));
    }

    private static void checkHorizontal(String name, int value) {
        if (value < -CatalogLimits.WORLD_HORIZONTAL_LIMIT || value > CatalogLimits.WORLD_HORIZONTAL_LIMIT) {
            throw new IllegalArgumentException("Координата " + name + "=" + value + " вне диапазона [-"
                    + CatalogLimits.WORLD_HORIZONTAL_LIMIT + ", " + CatalogLimits.WORLD_HORIZONTAL_LIMIT + "]");
        }
    }
}
