package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record FillBucketParams(FluidKind fluid, int radius) {
    public static final int RADIUS_DEFAULT = 8;
    public static final int RADIUS_MAX = 32;

    public FillBucketParams {
        Objects.requireNonNull(fluid, "Жидкость не может быть null: без неё неизвестно, что набирать");
        if (radius < 1 || radius > RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска источника " + radius + " вне диапазона [1, " + RADIUS_MAX
                    + "]: больший радиус гонит духа через полкарты");
        }
    }

    public static FillBucketParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new FillBucketParams(
                FluidKind.parse(ScriptArgs.requireString(params, "fluid")),
                ScriptArgs.optionalInt(params, "radius").orElse(RADIUS_DEFAULT));
    }
}
