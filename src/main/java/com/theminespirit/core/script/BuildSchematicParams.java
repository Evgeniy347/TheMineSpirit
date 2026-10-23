package com.theminespirit.core.script;

import com.theminespirit.core.building.BuildRotation;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record BuildSchematicParams(
        String file, int x, int y, int z, @Nullable BuildRotation rotation, int segments) {
    public static final int SEGMENTS_FROM_FILE = 0;
    public static final int SEGMENTS_MAX = 64;

    public BuildSchematicParams(String file, int x, int y, int z, @Nullable BuildRotation rotation) {
        this(file, x, y, z, rotation, SEGMENTS_FROM_FILE);
    }

    public BuildSchematicParams {
        if (file == null || file.isBlank()) {
            throw new IllegalArgumentException(
                    "Не задан файл для build_schematic: без него скрипт не знает, что строить");
        }
        BuildingCoords.checkX(x);
        BuildingCoords.checkY(y);
        BuildingCoords.checkZ(z);
        rotation = rotation == null ? BuildRotation.NONE : rotation;
        if (segments < SEGMENTS_FROM_FILE || segments > SEGMENTS_MAX) {
            throw new IllegalArgumentException("Параметр 'segments' " + segments + " вне " + SEGMENTS_FROM_FILE + ".."
                    + SEGMENTS_MAX + ": число сегментов подземной схемы, 0 — как в файле");
        }
    }

    public static BuildSchematicParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new BuildSchematicParams(
                ScriptArgs.requireString(params, "file"),
                ScriptArgs.requireInt(params, "x"),
                ScriptArgs.requireInt(params, "y"),
                ScriptArgs.requireInt(params, "z"),
                rotation(params),
                ScriptArgs.optionalInt(params, "segments").orElse(SEGMENTS_FROM_FILE));
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
