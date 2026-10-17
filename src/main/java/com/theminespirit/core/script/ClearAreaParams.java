package com.theminespirit.core.script;

import com.theminespirit.core.building.BuildBox;
import java.util.Map;
import java.util.Objects;

public record ClearAreaParams(int x1, int y1, int z1, int x2, int y2, int z2, boolean demolish) {
    public ClearAreaParams {
        BuildingCoords.checkX(x1);
        BuildingCoords.checkY(y1);
        BuildingCoords.checkZ(z1);
        BuildingCoords.checkX(x2);
        BuildingCoords.checkY(y2);
        BuildingCoords.checkZ(z2);
        BuildBox box = BuildBox.of(x1, y1, z1, x2, y2, z2);
        if (box.exceeds(CatalogLimits.BUILD_VOLUME_MAX)) {
            throw new IllegalArgumentException("Объём clear_area " + box.volume() + " больше предела "
                    + CatalogLimits.BUILD_VOLUME_MAX + ": разбейте область на части");
        }
    }

    public ClearAreaParams(int x1, int y1, int z1, int x2, int y2, int z2) {
        this(x1, y1, z1, x2, y2, z2, false);
    }

    public static ClearAreaParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new ClearAreaParams(
                ScriptArgs.requireInt(params, "x1"),
                ScriptArgs.requireInt(params, "y1"),
                ScriptArgs.requireInt(params, "z1"),
                ScriptArgs.requireInt(params, "x2"),
                ScriptArgs.requireInt(params, "y2"),
                ScriptArgs.requireInt(params, "z2"),
                ScriptArgs.optionalBoolean(params, "demolish", false));
    }
}
