package com.theminespirit.core.script;

import com.theminespirit.core.building.BuildBox;
import java.util.Map;
import java.util.Objects;

public record FillParams(String block, int x1, int y1, int z1, int x2, int y2, int z2) {
    public FillParams {
        if (block == null || block.isBlank()) {
            throw new IllegalArgumentException("Не задан блок для fill: без него скрипт не знает, чем заливать");
        }
        BuildingCoords.checkX(x1);
        BuildingCoords.checkY(y1);
        BuildingCoords.checkZ(z1);
        BuildingCoords.checkX(x2);
        BuildingCoords.checkY(y2);
        BuildingCoords.checkZ(z2);
        BuildBox box = BuildBox.of(x1, y1, z1, x2, y2, z2);
        if (box.exceeds(CatalogLimits.BUILD_VOLUME_MAX)) {
            throw new IllegalArgumentException("Объём fill " + box.volume() + " больше предела "
                    + CatalogLimits.BUILD_VOLUME_MAX + ": разбейте область на части");
        }
    }

    public static FillParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new FillParams(
                ScriptArgs.requireString(params, "block"),
                ScriptArgs.requireInt(params, "x1"),
                ScriptArgs.requireInt(params, "y1"),
                ScriptArgs.requireInt(params, "z1"),
                ScriptArgs.requireInt(params, "x2"),
                ScriptArgs.requireInt(params, "y2"),
                ScriptArgs.requireInt(params, "z2"));
    }
}
