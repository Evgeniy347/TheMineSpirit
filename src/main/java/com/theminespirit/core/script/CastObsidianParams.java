package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record CastObsidianParams(int count, int radius) {
    public static final int RADIUS_DEFAULT = 16;

    public CastObsidianParams {
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число обсидиана " + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: столько не налить и не добыть за один заход");
        }
        if (radius < 1 || radius > 32) {
            throw new IllegalArgumentException("Радиус поиска лавы " + radius
                    + " вне диапазона [1, 32]: больший радиус гонит духа через полкарты");
        }
    }

    public static CastObsidianParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new CastObsidianParams(ScriptArgs.requireInt(params, "count"), ScriptArgs.requireInt(params, "radius"));
    }
}
