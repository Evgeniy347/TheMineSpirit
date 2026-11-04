package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record PourBucketParams(FluidKind fluid, int x, int y, int z) {
    public PourBucketParams {
        Objects.requireNonNull(fluid, "Жидкость не может быть null: без неё неизвестно, что лить");
        BuildingCoords.checkX(x);
        BuildingCoords.checkY(y);
        BuildingCoords.checkZ(z);
    }

    public static PourBucketParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new PourBucketParams(
                FluidKind.parse(ScriptArgs.requireString(params, "fluid")),
                ScriptArgs.requireInt(params, "x"),
                ScriptArgs.requireInt(params, "y"),
                ScriptArgs.requireInt(params, "z"));
    }
}
