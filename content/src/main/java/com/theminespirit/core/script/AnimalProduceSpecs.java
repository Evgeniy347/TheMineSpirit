package com.theminespirit.core.script;

import java.util.List;

final class AnimalProduceSpecs {
    private static final String RADIUS = "radius";
    private static final String COUNT = "count";

    private AnimalProduceSpecs() {}

    private static ScriptSpec spec(
            String id, String summary, List<ParamSpec> params, List<String> preconditions, List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects);
    }

    static List<ScriptSpec> all() {
        return List.of(
                spec(
                        "milk",
                        "подоить коров",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.HERD_RADIUS_MIN,
                                        CatalogLimits.HERD_RADIUS_MAX,
                                        CatalogLimits.HERD_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        COUNT,
                                        false,
                                        CatalogLimits.HERD_COUNT_MIN,
                                        CatalogLimits.HERD_COUNT_MAX,
                                        CatalogLimits.HERD_WORK_COUNT_DEFAULT)),
                        List.of("есть пустые вёдра"),
                        List.of("коровы подоены")),
                spec(
                        "collect_eggs",
                        "собрать яйца в загоне",
                        List.of(ParamSpec.integer(
                                RADIUS,
                                false,
                                CatalogLimits.HERD_RADIUS_MIN,
                                CatalogLimits.HERD_RADIUS_MAX,
                                CatalogLimits.HERD_RADIUS_DEFAULT)),
                        List.of(),
                        List.of("яйца в инвентаре")),
                spec(
                        "harvest_hive",
                        "собрать соты или мёд из полного улья",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.HERD_RADIUS_MIN,
                                        CatalogLimits.HERD_RADIUS_MAX,
                                        CatalogLimits.HERD_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        COUNT,
                                        false,
                                        CatalogLimits.HERD_COUNT_MIN,
                                        CatalogLimits.HERD_COUNT_MAX,
                                        CatalogLimits.HIVE_COUNT_DEFAULT)),
                        List.of("есть ножницы или стеклянная бутылка", "под ульем костёр или материалы для него"),
                        List.of("соты или мёд в инвентаре", "пчёлы целы")),
                spec(
                        "brush",
                        "почистить броненосца щёткой",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.HERD_RADIUS_MIN,
                                        CatalogLimits.HERD_RADIUS_MAX,
                                        CatalogLimits.HERD_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        COUNT,
                                        false,
                                        CatalogLimits.HERD_COUNT_MIN,
                                        CatalogLimits.HERD_COUNT_MAX,
                                        CatalogLimits.HERD_WORK_COUNT_DEFAULT)),
                        List.of("есть щётка", "рядом взрослый броненосец"),
                        List.of("щитины броненосца подобраны")));
    }
}
