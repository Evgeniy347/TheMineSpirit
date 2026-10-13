package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record BuildSchematicParams(String file, int x, int y, int z) {
    public BuildSchematicParams {
        if (file == null || file.isBlank()) {
            throw new IllegalArgumentException("Не задан файл для build_schematic: без него скрипт не знает, что строить");
        }
        BuildingCoords.checkX(x);
        BuildingCoords.checkY(y);
        BuildingCoords.checkZ(z);
    }

    public static BuildSchematicParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new BuildSchematicParams(
                ScriptArgs.requireString(params, "file"),
                ScriptArgs.requireInt(params, "x"),
                ScriptArgs.requireInt(params, "y"),
                ScriptArgs.requireInt(params, "z"));
    }
}
