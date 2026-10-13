package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record PlaceBlockParams(String block, int x, int y, int z) {
    public PlaceBlockParams {
        if (block == null || block.isBlank()) {
            throw new IllegalArgumentException("Не задан блок для place_block: без него скрипт не знает, что ставить");
        }
        BuildingCoords.checkX(x);
        BuildingCoords.checkY(y);
        BuildingCoords.checkZ(z);
    }

    public static PlaceBlockParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new PlaceBlockParams(
                ScriptArgs.requireString(params, "block"),
                ScriptArgs.requireInt(params, "x"),
                ScriptArgs.requireInt(params, "y"),
                ScriptArgs.requireInt(params, "z"));
    }
}
