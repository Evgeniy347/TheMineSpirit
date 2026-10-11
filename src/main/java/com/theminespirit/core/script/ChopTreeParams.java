package com.theminespirit.core.script;

import com.theminespirit.core.forestry.TreeSpecies;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ChopTreeParams(List<TreeSpecies> species, int count, int radius, boolean replant) {
    public ChopTreeParams {
        Objects.requireNonNull(species, "species не может быть null: без породы непонятно, что рубить");
        species = List.copyOf(species);
        if (species.isEmpty()) {
            throw new IllegalArgumentException("Не выбрана ни одна порода: выбирайте из " + TreeSpecies.names());
        }
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("count=" + count + " вне диапазона [" + CatalogLimits.COUNT_MIN + ", "
                    + CatalogLimits.COUNT_MAX + "]: число деревьев должно лежать в этом промежутке");
        }
        if (radius < CatalogLimits.CHOP_RADIUS_MIN || radius > CatalogLimits.CHOP_RADIUS_MAX) {
            throw new IllegalArgumentException("radius=" + radius + " вне диапазона [" + CatalogLimits.CHOP_RADIUS_MIN
                    + ", " + CatalogLimits.CHOP_RADIUS_MAX + "]: радиус поиска деревьев должен лежать в этом промежутке");
        }
    }

    public static ChopTreeParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new ChopTreeParams(
                TreeSpecies.parse(ScriptArgs.requireString(params, "species")),
                ScriptArgs.requireInt(params, "count"),
                ScriptArgs.requireInt(params, "radius"),
                ScriptArgs.requireBool(params, "replant"));
    }
}
