package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record BuildShelterParams(int side) {
    public BuildShelterParams {
        if (side < CatalogLimits.SHELTER_SIDE_MIN || side > CatalogLimits.SHELTER_SIDE_MAX) {
            throw new IllegalArgumentException("Сторона укрытия " + side + ": укрытие бывает 3×3 или 4×4 " + "(пределы "
                    + CatalogLimits.SHELTER_SIDE_MIN + ".." + CatalogLimits.SHELTER_SIDE_MAX + ")");
        }
    }

    public static BuildShelterParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new BuildShelterParams(ScriptArgs.requireInt(params, "side"));
    }
}
