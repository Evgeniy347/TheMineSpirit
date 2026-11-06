package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record LightPortalParams(int radius) {
    public static final int RADIUS_DEFAULT = 16;

    public LightPortalParams {
        if (radius < 1 || radius > 32) {
            throw new IllegalArgumentException("Радиус поиска рамки портала " + radius
                    + " вне диапазона [1, 32]: больший радиус гоняет духа по всему поселению");
        }
    }

    public static LightPortalParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new LightPortalParams(ScriptArgs.requireInt(params, "radius"));
    }
}
