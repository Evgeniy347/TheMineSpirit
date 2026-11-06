package com.theminespirit.core.script;

import com.theminespirit.core.husbandry.Species;
import java.util.Map;
import java.util.Objects;

public record BreedParams(Species species, int radius, int pairs) {
    public BreedParams {
        Objects.requireNonNull(species, "Вид не может быть null: пару сводят внутри одного вида");
        if (radius < CatalogLimits.HERD_RADIUS_MIN || radius > CatalogLimits.HERD_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.HERD_RADIUS_MIN + ", " + CatalogLimits.HERD_RADIUS_MAX
                    + "]: шире загона пару не собрать");
        }
        if (pairs < CatalogLimits.HERD_PAIRS_MIN || pairs > CatalogLimits.HERD_PAIRS_MAX) {
            throw new IllegalArgumentException("Число пар " + pairs + " вне диапазона ["
                    + CatalogLimits.HERD_PAIRS_MIN + ", " + CatalogLimits.HERD_PAIRS_MAX
                    + "]: за один запуск стадо прирастает ограниченно");
        }
    }

    public static BreedParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new BreedParams(
                Species.parse(ScriptArgs.requireString(params, "species")),
                ScriptArgs.requireInt(params, "radius"),
                ScriptArgs.requireInt(params, "pairs"));
    }
}
