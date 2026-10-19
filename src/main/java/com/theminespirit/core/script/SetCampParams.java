package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record SetCampParams(Optional<Integer> x, Optional<Integer> y, Optional<Integer> z, int radius) {
    public SetCampParams {
        if (x == null || y == null || z == null) {
            throw new IllegalArgumentException(
                    "Координаты лагеря не могут быть null: нет координаты — Optional.empty(), "
                            + "иначе скрипт не отличит «на месте духа» от сломанного плана");
        }
        x.ifPresent(BuildingCoords::checkX);
        y.ifPresent(BuildingCoords::checkY);
        z.ifPresent(BuildingCoords::checkZ);
        if (radius < CatalogLimits.CAMP_RADIUS_MIN || radius > CatalogLimits.CAMP_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус лагеря = " + radius + ": спокойная зона обязана лежать в ["
                    + CatalogLimits.CAMP_RADIUS_MIN + ", " + CatalogLimits.CAMP_RADIUS_MAX + "]");
        }
    }

    public static SetCampParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new SetCampParams(
                ScriptArgs.optionalInt(params, "x"),
                ScriptArgs.optionalInt(params, "y"),
                ScriptArgs.optionalInt(params, "z"),
                ScriptArgs.optionalInt(params, "radius").orElse(CatalogLimits.CAMP_RADIUS_DEFAULT));
    }
}
