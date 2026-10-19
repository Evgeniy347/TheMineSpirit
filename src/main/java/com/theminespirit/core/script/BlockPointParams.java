package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record BlockPointParams(int x, int y, int z) {
    public BlockPointParams {
        checkCoordinate("x", x, CatalogLimits.WORLD_HORIZONTAL_LIMIT);
        checkY(y);
        checkCoordinate("z", z, CatalogLimits.WORLD_HORIZONTAL_LIMIT);
    }

    public static BlockPointParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new BlockPointParams(
                ScriptArgs.requireInt(params, "x"),
                ScriptArgs.requireInt(params, "y"),
                ScriptArgs.requireInt(params, "z"));
    }

    private static void checkCoordinate(String name, int value, int limit) {
        if (value < -limit || value > limit) {
            throw new IllegalArgumentException(
                    "Координата " + name + "=" + value + " вне диапазона [-" + limit + ", " + limit + "]");
        }
    }

    private static void checkY(int value) {
        if (value < CatalogLimits.WORLD_Y_MIN || value > CatalogLimits.WORLD_Y_MAX) {
            throw new IllegalArgumentException("Координата y=" + value + " вне диапазона [" + CatalogLimits.WORLD_Y_MIN
                    + ", " + CatalogLimits.WORLD_Y_MAX + "]");
        }
    }
}
