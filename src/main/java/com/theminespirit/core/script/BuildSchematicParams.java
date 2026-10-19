package com.theminespirit.core.script;

import com.theminespirit.core.building.BuildRotation;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record BuildSchematicParams(
        String file, int x, int y, int z, @Nullable BuildRotation rotation) {
    public BuildSchematicParams {
        if (file == null || file.isBlank()) {
            throw new IllegalArgumentException(
                    "Не задан файл для build_schematic: без него скрипт не знает, что строить");
        }
        BuildingCoords.checkX(x);
        BuildingCoords.checkY(y);
        BuildingCoords.checkZ(z);
        rotation = rotation == null ? BuildRotation.NONE : rotation;
    }

    public static BuildSchematicParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new BuildSchematicParams(
                ScriptArgs.requireString(params, "file"),
                ScriptArgs.requireInt(params, "x"),
                ScriptArgs.requireInt(params, "y"),
                ScriptArgs.requireInt(params, "z"),
                rotation(params));
    }

    private static BuildRotation rotation(Map<String, Object> params) {
        Object value = params.get("rotation");
        if (value == null) {
            return BuildRotation.NONE;
        }
        if (value instanceof String text) {
            return BuildRotation.parse(text);
        }
        if (value instanceof Integer turns) {
            return BuildRotation.ofQuarterTurns(turns);
        }
        throw new IllegalArgumentException("Параметр 'rotation' скрипта должен быть строкой или целым числом, "
                + "а получено " + value.getClass().getSimpleName() + ": поворот задаётся именем или четвертями");
    }
}
