package com.theminespirit.core.script;

import java.util.List;

public final class FillAndClearScriptSpecs {
    private static final String BLOCK = "block";

    private FillAndClearScriptSpecs() {}

    public static List<ScriptSpec> all() {
        return List.of(
                spec(
                        "fill",
                        "заполнить область блоком",
                        List.of(
                                ParamSpec.text(BLOCK, true),
                                ParamSpec.integer(
                                        "x1",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y1", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z1",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer(
                                        "x2",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y2", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z2",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of(),
                        List.of("область заполнена")),
                spec(
                        "clear_area",
                        "очистить область",
                        List.of(
                                ParamSpec.integer(
                                        "x1",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y1", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z1",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer(
                                        "x2",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y2", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z2",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.bool("demolish", false, false),
                                ParamSpec.bool("keep_ground", false, false)),
                        List.of(),
                        List.of("область очищена")),
                spec(
                        "clear_high",
                        "снести высокие столбы с временной подмости",
                        List.of(
                                ParamSpec.text("scaffold", true),
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
                                ParamSpec.integer("height", true, 1, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.text(BLOCK, false, "auto"),
                                ParamSpec.textList("cells", true),
                                ParamSpec.textList("tower", true)),
                        List.of("есть дешёвые блоки на башню"),
                        List.of("столбы снесены, своя башня разобрана")));
    }

    private static ScriptSpec spec(
            String id, String summary, List<ParamSpec> params, List<String> preconditions, List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects);
    }
}
