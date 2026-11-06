package com.theminespirit.core.script;

import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.perception.LocateQuery;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LocateParams {
    private LocateParams() {}

    public static LocateQuery block(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        IdFilter filter = IdFilter.of(ScriptArgs.requireStringList(params, "filter"));
        if (filter.matchesAll()) {
            throw new IllegalArgumentException("Фильтр поиска блока пуст или '*': ближайший блок «любого вида» — "
                    + "клетка под духом, назовите искомый блок (minecraft:diamond_ore, *_ore)");
        }
        return LocateQuery.block(filter, radius(params));
    }

    public static LocateQuery tree(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        String species = ScriptArgs.requireString(params, "species");
        TreeSpecies.parse(species);
        return LocateQuery.tree(species, radius(params));
    }

    public static LocateQuery container(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return LocateQuery.container(radius(params));
    }

    public static List<TreeSpecies> speciesOf(LocateQuery query) {
        Objects.requireNonNull(query, "query не может быть null: порода берётся из запроса поиска дерева");
        return TreeSpecies.parse(query.species());
    }

    private static ScanRadius radius(Map<String, Object> params) {
        return ScanRadius.of(ScriptArgs.requireInt(params, "radius"));
    }
}
