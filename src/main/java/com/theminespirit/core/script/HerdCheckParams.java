package com.theminespirit.core.script;

import com.theminespirit.core.husbandry.Species;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record HerdCheckParams(List<Species> species, int radius) {
    public HerdCheckParams {
        Objects.requireNonNull(species, "Виды не могут быть null: «все виды» задают Species.ALL");
        species = List.copyOf(species);
        if (species.isEmpty()) {
            throw new IllegalArgumentException("Не выбран ни один вид: проверяйте из " + Species.names());
        }
        if (radius < CatalogLimits.HERD_RADIUS_MIN || radius > CatalogLimits.HERD_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.HERD_RADIUS_MIN + ", " + CatalogLimits.HERD_RADIUS_MAX
                    + "]: шире загона считать некого");
        }
    }

    public static HerdCheckParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new HerdCheckParams(
                Species.parseList(ScriptArgs.requireString(params, "species")),
                ScriptArgs.requireInt(params, "radius"));
    }
}
