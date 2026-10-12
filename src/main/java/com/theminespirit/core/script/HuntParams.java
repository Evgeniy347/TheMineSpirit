package com.theminespirit.core.script;

import com.theminespirit.core.husbandry.Species;
import java.util.Map;
import java.util.Objects;

public record HuntParams(Species species, int radius, int count) {
    public HuntParams {
        Objects.requireNonNull(species, "Вид не может быть null: охотятся на один вид за раз");
        if (radius < CatalogLimits.HERD_HUNT_RADIUS_MIN || radius > CatalogLimits.HERD_HUNT_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус охоты " + radius + " вне диапазона ["
                    + CatalogLimits.HERD_HUNT_RADIUS_MIN + ", " + CatalogLimits.HERD_HUNT_RADIUS_MAX
                    + "]: дальше дичь не выследить");
        }
        if (count < CatalogLimits.HERD_COUNT_MIN || count > CatalogLimits.HERD_COUNT_MAX) {
            throw new IllegalArgumentException("Число голов " + count + " вне диапазона ["
                    + CatalogLimits.HERD_COUNT_MIN + ", " + CatalogLimits.HERD_COUNT_MAX
                    + "]: за один выход бьют ограниченное число дичи");
        }
    }

    public static HuntParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new HuntParams(
                Species.parse(ScriptArgs.requireString(params, "species")),
                ScriptArgs.requireInt(params, "radius"),
                ScriptArgs.requireInt(params, "count"));
    }
}
