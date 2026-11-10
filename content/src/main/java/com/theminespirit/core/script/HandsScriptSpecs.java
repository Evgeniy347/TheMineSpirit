package com.theminespirit.core.script;

import java.util.List;

final class HandsScriptSpecs {
    private HandsScriptSpecs() {}

    private static final String TARGET = "target";
    private static final String BLOCK = "block";
    private static final String COUNT = "count";
    private static final String NEAREST = "nearest";
    private static final String ITEM = "item";
    private static final String CONTAINER = "container";
    private static final String RADIUS = "radius";

    static List<ScriptSpec> itemSpecs() {
        return List.of(
                spec(
                        "craft",
                        "скрафтить предмет",
                        List.of(
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                stationRadius(),
                                ParamSpec.bool("place", false, false)),
                        List.of("есть материалы и рецепт"),
                        List.of("предмет скрафчен")),
                spec(
                        "smelt",
                        "переплавить предмет",
                        List.of(
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                stationRadius()),
                        List.of("есть печь и топливо"),
                        List.of("предмет переплавлен")),
                spec(
                        "equip",
                        "надеть предмет",
                        List.of(ParamSpec.text(ITEM, true)),
                        List.of("предмет есть в инвентаре"),
                        List.of("предмет надет")),
                spec(
                        "deposit",
                        "положить предметы в контейнер",
                        List.of(
                                ParamSpec.text(CONTAINER, false, NEAREST),
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                containerRadius(),
                                ParamSpec.bool("leftover", false, false)),
                        List.of("контейнер доступен"),
                        List.of("предметы в контейнере", "контейнер отсортирован")),
                spec(
                        "withdraw",
                        "взять предметы из контейнера",
                        List.of(
                                ParamSpec.text(CONTAINER, false, NEAREST),
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                containerRadius()),
                        List.of("контейнер доступен"),
                        List.of("предметы в инвентаре", "контейнер отсортирован")),
                spec(
                        "eat",
                        "съесть еду из инвентаря (без item — лучшее по питательности)",
                        List.of(ParamSpec.text(ITEM, false)),
                        List.of("в инвентаре есть еда"),
                        List.of("сытость духа выросла", "съеденное убрано из инвентаря")),
                spec(
                        "sort_chest",
                        "рассортировать содержимое сундука",
                        List.of(ParamSpec.text(CONTAINER, false, NEAREST), containerRadius()),
                        List.of("контейнер доступен"),
                        List.of("содержимое упорядочено")),
                spec(
                        "unload",
                        "разгрузить лишнее на базе",
                        List.of(
                                ParamSpec.text(CONTAINER, false, "auto"),
                                containerRadius(),
                                ParamSpec.bool("force", false, false)),
                        List.of("рядом есть допустимый контейнер"),
                        List.of("лишнее разложено по контейнерам", "базовый набор остался при духе")),
                spec(
                        "acquire",
                        "добыть предмет любым способом",
                        List.of(
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                ParamSpec.bool("fresh", false, false)),
                        List.of(),
                        List.of("предмет есть в инвентаре")));
    }

    static List<ScriptSpec> combatAndClimbSpecs() {
        return List.of(
                spec(
                        "attack",
                        "атаковать цель",
                        List.of(ParamSpec.text(TARGET, false, "hostile")),
                        List.of("цель видима"),
                        List.of("цель атакована")),
                spec(
                        "defend",
                        "защитить цель",
                        List.of(ParamSpec.text(TARGET, true)),
                        List.of("цель существует"),
                        List.of("цель защищена")),
                spec("retreat", "отступить", List.of(), List.of("есть путь отхода"), List.of("дух отступил")),
                spec(
                        "disengage",
                        "выйти из боя: отойти на дистанцию и прекратить бой",
                        List.of(
                                ParamSpec.text(TARGET, false, DisengageParams.DEFAULT_TARGET),
                                ParamSpec.integer(
                                        "distance",
                                        false,
                                        CatalogLimits.DISENGAGE_DISTANCE_MIN,
                                        CatalogLimits.DISENGAGE_DISTANCE_MAX,
                                        CatalogLimits.DISENGAGE_DISTANCE_DEFAULT)),
                        List.of("рядом есть с кем воевать или бой уже идёт"),
                        List.of("дух отошёл на дистанцию и больше не атакует")),
                spec(
                        "pillar_up",
                        "подняться столбом",
                        List.of(
                                ParamSpec.integer("height", true, 1, CatalogLimits.COUNT_MAX, 1),
                                ParamSpec.text(BLOCK, false, ""),
                                ParamSpec.bool("down", false, false)),
                        List.of("есть материал для блоков"),
                        List.of("дух поднялся на высоту")),
                spec(
                        "dig_out",
                        "выбраться из-под земли: лестница вверх (ступень 1, проход 2) в сторону точки",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.bool("rescue", false, false)),
                        List.of("дух замурован: воздуха к поверхности нет"),
                        List.of("дух под открытым небом или на высоте точки")),
                spec(
                        "pillar_down",
                        "разобрать свой столб и вернуть блоки",
                        List.of(),
                        List.of("под духом есть свой столб"),
                        List.of("блоки столба вернулись в инвентарь")),
                spec(
                        "climb",
                        "влезть по лестнице, лиане или строительным лесам вверх или вниз",
                        List.of(ParamSpec.bool("down", false, false)),
                        List.of("рядом есть колонна из лестниц, лиан или строительных лесов"),
                        List.of("дух дошёл до конца колонны: вверх — до верхней ступени, вниз — до нижней")));
    }

    private static ParamSpec stationRadius() {
        return ParamSpec.integer(
                RADIUS,
                false,
                CatalogLimits.STATION_RADIUS_MIN,
                CatalogLimits.STATION_RADIUS_MAX,
                CatalogLimits.STATION_RADIUS_DEFAULT);
    }

    private static ParamSpec containerRadius() {
        return ParamSpec.integer(
                RADIUS,
                false,
                CatalogLimits.CONTAINER_RADIUS_MIN,
                CatalogLimits.CONTAINER_RADIUS_MAX,
                CatalogLimits.CONTAINER_RADIUS_DEFAULT);
    }

    private static ScriptSpec spec(
            String id, String summary, List<ParamSpec> params, List<String> preconditions, List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects);
    }
}
