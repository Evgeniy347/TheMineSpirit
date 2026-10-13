package com.theminespirit.core.script;

import com.theminespirit.core.building.BuildBox;
import java.util.Map;
import java.util.Objects;

public record DigAreaParams(int x, int y, int z, int size) {
    public DigAreaParams {
        BuildingCoords.checkX(x);
        BuildingCoords.checkY(y);
        BuildingCoords.checkZ(z);
        if (size < CatalogLimits.DIG_SIZE_MIN || size > CatalogLimits.DIG_SIZE_MAX) {
            throw new IllegalArgumentException("Сторона куба " + size + " вне диапазона ["
                    + CatalogLimits.DIG_SIZE_MIN + ", " + CatalogLimits.DIG_SIZE_MAX
                    + "]: куб задаётся стороной от минимального угла");
        }
        BuildBox box = BuildBox.cube(x, y, z, size);
        if (box.exceeds(CatalogLimits.BUILD_VOLUME_MAX)) {
            throw new IllegalArgumentException("Объём dig_area " + box.volume() + " больше предела "
                    + CatalogLimits.BUILD_VOLUME_MAX + ": уменьшите сторону куба");
        }
    }

    public static DigAreaParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new DigAreaParams(
                ScriptArgs.requireInt(params, "x"),
                ScriptArgs.requireInt(params, "y"),
                ScriptArgs.requireInt(params, "z"),
                ScriptArgs.requireInt(params, "size"));
    }
}
