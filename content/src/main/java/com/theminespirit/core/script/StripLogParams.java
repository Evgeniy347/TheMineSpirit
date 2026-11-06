package com.theminespirit.core.script;

import com.theminespirit.core.forestry.TreeSpecies;
import java.util.Map;
import java.util.Objects;

public record StripLogParams(TreeSpecies species, int count, int radius) {
    public StripLogParams {
        Objects.requireNonNull(species, "species не может быть null: без породы непонятно, какие брёвна тесать");
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("count=" + count + " вне диапазона [" + CatalogLimits.COUNT_MIN + ", "
                    + CatalogLimits.COUNT_MAX + "]: число обтёсанных брёвен должно лежать в этом промежутке");
        }
        if (radius < CatalogLimits.CHOP_RADIUS_MIN || radius > CatalogLimits.CHOP_RADIUS_MAX) {
            throw new IllegalArgumentException("radius=" + radius + " вне диапазона [" + CatalogLimits.CHOP_RADIUS_MIN
                    + ", " + CatalogLimits.CHOP_RADIUS_MAX
                    + "]: радиус поиска брёвен должен лежать в этом промежутке");
        }
    }

    public static StripLogParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new StripLogParams(
                species(ScriptArgs.requireString(params, "species")),
                ScriptArgs.requireInt(params, "count"),
                ScriptArgs.requireInt(params, "radius"));
    }

    private static TreeSpecies species(String id) {
        return TreeSpecies.ALL.stream()
                .filter(tree -> tree.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестная порода '" + id
                        + "': обтёсывать можно брёвна пород "
                        + TreeSpecies.ALL.stream().map(TreeSpecies::id).toList()));
    }
}
