package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record ChopTargetParams(int x, int y, int z, boolean replant, boolean grove) {
    public static final String GROVE = "grove";

    public ChopTargetParams {
        checkHorizontal("x", x);
        checkHorizontal("z", z);
        if (y < CatalogLimits.WORLD_Y_MIN || y > CatalogLimits.WORLD_Y_MAX) {
            throw new IllegalArgumentException("Координата y=" + y + " вне диапазона [" + CatalogLimits.WORLD_Y_MIN
                    + ", " + CatalogLimits.WORLD_Y_MAX + "]: ствола дерева вне мира не бывает");
        }
    }

    public static ChopTargetParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new ChopTargetParams(
                ScriptArgs.requireInt(params, "x"),
                ScriptArgs.requireInt(params, "y"),
                ScriptArgs.requireInt(params, "z"),
                ScriptArgs.requireBoolean(params, "replant"),
                ScriptArgs.optionalBoolean(params, GROVE, false));
    }

    private static void checkHorizontal(String name, int value) {
        if (value < -CatalogLimits.WORLD_HORIZONTAL_LIMIT || value > CatalogLimits.WORLD_HORIZONTAL_LIMIT) {
            throw new IllegalArgumentException("Координата " + name + "=" + value + " вне диапазона [-"
                    + CatalogLimits.WORLD_HORIZONTAL_LIMIT + ", " + CatalogLimits.WORLD_HORIZONTAL_LIMIT + "]");
        }
    }
}
