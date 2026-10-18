package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record CrossWaterParams(int x, int y, int z, String block) {
    public CrossWaterParams {
        BuildingCoords.checkX(x);
        BuildingCoords.checkY(y);
        BuildingCoords.checkZ(z);
    }

    public static CrossWaterParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        String block = ScriptArgs.optionalString(params, "block", "");
        return new CrossWaterParams(
                ScriptArgs.requireInt(params, "x"),
                ScriptArgs.requireInt(params, "y"),
                ScriptArgs.requireInt(params, "z"),
                block.isBlank() ? null : block);
    }
}
