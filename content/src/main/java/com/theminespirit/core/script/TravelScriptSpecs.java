package com.theminespirit.core.script;

import java.util.List;

final class TravelScriptSpecs {
    private TravelScriptSpecs() {}

    private static final String RADIUS = "radius";
    private static final String TARGET = "target";
    private static final String BLOCK = "block";
    private static final String COUNT = "count";
    private static final String NEAREST = "nearest";

    static List<ScriptSpec> travelSpecs() {
        return List.of(
                spec(
                        "goto",
                        "пройти к заданной точке",
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
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("точка достижима"),
                        List.of("дух находится в точке")),
                spec(
                        "follow",
                        "следовать за целью на расстоянии",
                        List.of(
                                ParamSpec.text(TARGET, true),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.FOLLOW_RADIUS_MIN,
                                        CatalogLimits.FOLLOW_RADIUS_MAX,
                                        CatalogLimits.FOLLOW_RADIUS_DEFAULT)),
                        List.of("цель существует"),
                        List.of("дух держит дистанцию до цели")),
                spec(
                        "escort",
                        "сопровождать и охранять цель, ожидая рядом с ней",
                        List.of(
                                ParamSpec.text(TARGET, true),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.FOLLOW_RADIUS_MIN,
                                        CatalogLimits.FOLLOW_RADIUS_MAX,
                                        CatalogLimits.FOLLOW_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        "until",
                                        false,
                                        CatalogLimits.ESCORT_UNTIL_MIN,
                                        CatalogLimits.ESCORT_UNTIL_MAX,
                                        CatalogLimits.ESCORT_UNTIL_DEFAULT)),
                        List.of("цель существует"),
                        List.of("дух рядом с целью и защищает её тактикой обороны")),
                spec(
                        "return_home",
                        "вернуться домой (дом задаёт команда /spirit home)",
                        List.of(),
                        List.of("дом задан"),
                        List.of("дух дома")),
                spec(
                        "patrol",
                        "патрулировать область по маршруту",
                        List.of(
                                ParamSpec.textList("points", true),
                                ParamSpec.enumeration(
                                        "mode",
                                        false,
                                        List.of("loop", "ping_pong"),
                                        CatalogLimits.NAV_PATROL_MODE_DEFAULT),
                                ParamSpec.integer(
                                        "loops",
                                        false,
                                        CatalogLimits.NAV_PATROL_LOOPS_MIN,
                                        CatalogLimits.NAV_PATROL_LOOPS_MAX,
                                        CatalogLimits.NAV_PATROL_LOOPS_DEFAULT)),
                        List.of("маршрут задан"),
                        List.of("маршрут пройден")));
    }

    static List<ScriptSpec> navigationSpecs() {
        return List.of(
                spec(
                        "goto_block",
                        "подойти к ближайшему блоку заданного вида",
                        List.of(
                                ParamSpec.text(BLOCK, true),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.NAV_RADIUS_MIN,
                                        CatalogLimits.NAV_RADIUS_MAX,
                                        CatalogLimits.NAV_RADIUS_DEFAULT),
                                ParamSpec.integer(COUNT, false, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX, 1)),
                        List.of("в радиусе есть подходящие блоки"),
                        List.of("дух в пределах досягаемости блока")),
                spec(
                        "come_to_player",
                        "подойти к игроку",
                        List.of(ParamSpec.text("player", false, NEAREST)),
                        List.of("игрок на сервере"),
                        List.of("дух рядом с игроком")),
                spec(
                        "wander",
                        "бродить вокруг старта",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.NAV_RADIUS_MIN,
                                        CatalogLimits.NAV_RADIUS_MAX,
                                        CatalogLimits.NAV_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        "duration_ticks",
                                        false,
                                        CatalogLimits.NAV_WANDER_DURATION_MIN,
                                        CatalogLimits.NAV_WANDER_DURATION_MAX,
                                        CatalogLimits.NAV_WANDER_DURATION_DEFAULT)),
                        List.of(),
                        List.of("время блуждания прошло")),
                spec(
                        "explore",
                        "обойти область по спирали и запомнить ресурсы и опасности",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.NAV_RADIUS_MIN,
                                        CatalogLimits.NAV_RADIUS_MAX,
                                        CatalogLimits.NAV_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        "budget",
                                        false,
                                        CatalogLimits.EXPLORE_BUDGET_MIN,
                                        CatalogLimits.EXPLORE_BUDGET_MAX,
                                        CatalogLimits.EXPLORE_BUDGET_DEFAULT)),
                        List.of("вход за границу поселения разрешён группой или игроком, ночью — нет"),
                        List.of("ресурсы и опасности записаны в память", "дух вернулся к точке старта")),
                spec(
                        "keep_distance",
                        "держать дистанцию до цели",
                        List.of(
                                ParamSpec.text(TARGET, true),
                                ParamSpec.integer(
                                        "distance",
                                        false,
                                        CatalogLimits.NAV_KEEP_DISTANCE_MIN,
                                        CatalogLimits.NAV_KEEP_DISTANCE_MAX,
                                        CatalogLimits.NAV_KEEP_DISTANCE_DEFAULT)),
                        List.of("цель существует"),
                        List.of("дух держит дистанцию до цели")));
    }

    private static ScriptSpec spec(
            String id, String summary, List<ParamSpec> params, List<String> preconditions, List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects);
    }
}
