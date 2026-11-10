package com.theminespirit.core.script;

import java.util.List;

final class TerrainScriptSpecs {
    private TerrainScriptSpecs() {}

    private static final String BLOCK = "block";

    static List<ScriptSpec> terrainSpecs() {
        return List.of(
                spec(
                        "walk_careful",
                        "осторожно пройти к точке",
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
                        "step_over",
                        "шагнуть на соседнюю клетку",
                        List.of(ParamSpec.integer("dx", true, -1, 1), ParamSpec.integer("dz", true, -1, 1)),
                        List.of("соседняя клетка проходима"),
                        List.of("дух на соседней клетке")),
                spec(
                        "approach",
                        "подойти к цели на стоянку в досягаемости",
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
                        List.of("к цели есть подход"),
                        List.of("дух на стоянке в досягаемости цели")),
                spec(
                        "swim",
                        "плыть к цели, не тонуть",
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
                        List.of("цель за водой или на воде"),
                        List.of("дух переплыл к точке, не утонув")));
    }

    static List<ScriptSpec> waterAndFleeSpecs() {
        return List.of(
                spec(
                        "cross_water",
                        "переправиться через воду вплавь (мост в переправе не строится)",
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
                        List.of("между духом и целью есть вода"),
                        List.of("дух на другой стороне: переплыл узкую воду или перешёл по мосту")),
                spec(
                        "bridge",
                        "построить мост через провал или воду из блоков инвентаря",
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
                                ParamSpec.integer(
                                        "length",
                                        true,
                                        CatalogLimits.BRIDGE_LENGTH_MIN,
                                        CatalogLimits.BRIDGE_LENGTH_MAX),
                                ParamSpec.text(BLOCK, false, "")),
                        List.of("в инвентаре есть дешёвые блоки", "впереди нет лавы"),
                        List.of("мост поставлен, дух на другой стороне")),
                spec(
                        "flee",
                        "убежать от угрозы",
                        List.of(),
                        List.of("поблизости есть угроза"),
                        List.of("дух вне опасности")));
    }

    private static ScriptSpec spec(
            String id, String summary, List<ParamSpec> params, List<String> preconditions, List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects);
    }
}
