package com.theminespirit.core.script;

import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.mining.PickaxeTiers;
import java.util.List;

final class GatheringSpecs {
    private static final String BLOCK = "block";
    private static final String COUNT = "count";
    private static final String RADIUS = "radius";
    private static final String FILTER = "filter";
    private static final String CROP = "crop";

    private GatheringSpecs() {}

    static List<ScriptSpec> all() {
        return List.of(
                spec(
                        "gather",
                        "собрать блоки заданного типа",
                        List.of(
                                ParamSpec.text(BLOCK, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.HARVEST_RADIUS_MIN,
                                        CatalogLimits.HARVEST_RADIUS_MAX,
                                        CatalogLimits.HARVEST_RADIUS_DEFAULT)),
                        List.of("в радиусе есть подходящие блоки"),
                        pickaxeForBlock(),
                        List.of("блоки собраны")),
                spec(
                        "collect_drops",
                        "подобрать выпавшие предметы вокруг духа",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.DROP_RADIUS_MIN,
                                        CatalogLimits.DROP_RADIUS_MAX,
                                        CatalogLimits.DROP_RADIUS_DEFAULT),
                                ParamSpec.textList(FILTER, false),
                                ParamSpec.enumeration("overflow", false, List.of("fail", "by_value"), "by_value")),
                        List.of("в инвентаре есть место"),
                        List.of(
                                "предметы по фильтру в радиусе подобраны",
                                "предметов по фильтру в радиусе не осталось")),
                spec(
                        "harvest_crop",
                        "собрать урожай",
                        List.of(
                                ParamSpec.enumeration(CROP, false, CropKind.names(), "any"),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.FARM_RADIUS_MIN,
                                        CatalogLimits.FARM_RADIUS_MAX,
                                        CatalogLimits.FARM_RADIUS_DEFAULT),
                                ParamSpec.bool("replant", false, true)),
                        List.of("посевы созрели"),
                        List.of("урожай собран")),
                spec(
                        "plant_crop",
                        "посадить культуру",
                        List.of(
                                ParamSpec.enumeration(CROP, true, CropKind.plantableNames()),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.FARM_RADIUS_MIN,
                                        CatalogLimits.FARM_RADIUS_MAX,
                                        CatalogLimits.FARM_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        COUNT,
                                        false,
                                        CatalogLimits.COUNT_MIN,
                                        CatalogLimits.COUNT_MAX,
                                        CatalogLimits.PLANT_COUNT_DEFAULT)),
                        List.of("есть семена", "есть подготовленная земля"),
                        List.of("культура посажена")));
    }

    private static ScriptSpec spec(
            String id, String summary, List<ParamSpec> params, List<String> preconditions, List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects);
    }

    private static ScriptSpec spec(
            String id,
            String summary,
            List<ParamSpec> params,
            List<String> preconditions,
            List<ScriptRequirement> requires,
            List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects, requires);
    }

    private static List<ScriptRequirement> pickaxeForBlock() {
        return List.of(ScriptRequirement.pickaxe(BLOCK, PickaxeTiers.blockTiers(), ScriptRequirement.NO_TIER));
    }
}
