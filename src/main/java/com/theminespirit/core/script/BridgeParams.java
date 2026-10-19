package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record BridgeParams(
        int x, int y, int z, int length, @Nullable String block) {
    public BridgeParams {
        BuildingCoords.checkX(x);
        BuildingCoords.checkY(y);
        BuildingCoords.checkZ(z);
        if (length < CatalogLimits.BRIDGE_LENGTH_MIN || length > CatalogLimits.BRIDGE_LENGTH_MAX) {
            throw new IllegalArgumentException("Длина моста " + length + " вне диапазона ["
                    + CatalogLimits.BRIDGE_LENGTH_MIN + ", " + CatalogLimits.BRIDGE_LENGTH_MAX
                    + "]: мост такой длины не перекинуть");
        }
    }

    public static BridgeParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        String block = ScriptArgs.optionalString(params, "block", "");
        return new BridgeParams(
                ScriptArgs.requireInt(params, "x"),
                ScriptArgs.requireInt(params, "y"),
                ScriptArgs.requireInt(params, "z"),
                ScriptArgs.requireInt(params, "length"),
                block.isBlank() ? null : block);
    }
}
