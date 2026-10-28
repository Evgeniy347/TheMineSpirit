package com.theminespirit.core.script;

import com.theminespirit.core.forestry.TreeSpecies;
import java.util.List;

final class GatherScriptSpecs {
    private GatherScriptSpecs() {}

    static ScriptSpec stripLog() {
        return new ScriptSpec(
                "strip_log",
                "обтесать брёвна топором и забрать обтёсанные",
                List.of(
                        ParamSpec.enumeration(
                                "species",
                                true,
                                TreeSpecies.ALL.stream().map(TreeSpecies::id).toList()),
                        ParamSpec.integer("count", false, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX, 1),
                        ParamSpec.integer(
                                "radius",
                                false,
                                CatalogLimits.CHOP_RADIUS_MIN,
                                CatalogLimits.CHOP_RADIUS_MAX,
                                CatalogLimits.HARVEST_RADIUS_DEFAULT)),
                List.of("в инвентаре есть топор", "в радиусе есть брёвна породы"),
                List.of("брёвна обтёсаны", "обтёсанные брёвна подобраны в инвентарь"));
    }
}
