package com.theminespirit.core.script;

import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.farming.CropKind;
import java.util.List;

final class BuildScriptSpecs {
    private BuildScriptSpecs() {}

    private static final String RADIUS = "radius";
    private static final String BLOCK = "block";

    static List<ScriptSpec> placementSpecs() {
        return List.of(
                spec(
                        "place_block",
                        "поставить блок",
                        List.of(
                                ParamSpec.text(BLOCK, true),
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
                        List.of("место свободно"),
                        List.of("блок поставлен")),
                spec(
                        "light_area",
                        "осветить территорию факелами",
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
                                        RADIUS,
                                        false,
                                        CatalogLimits.LIGHT_RADIUS_MIN,
                                        CatalogLimits.LIGHT_RADIUS_MAX,
                                        CatalogLimits.LIGHT_RADIUS_DEFAULT)),
                        List.of("в области есть куда ставить факелы", "в инвентаре есть факелы"),
                        List.of("на земле не осталось тёмных клеток", "итог называет, сколько факелов не хватило")));
    }

    static List<ScriptSpec> fillAndClearSpecs() {
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
                        List.of("область очищена")));
    }

    static List<ScriptSpec> buildSpecs() {
        return List.of(
                spec(
                        "build_schematic",
                        "построить схему из файла",
                        List.of(
                                ParamSpec.text("file", true),
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
                                ParamSpec.enumeration(
                                        "rotation",
                                        false,
                                        List.of("none", "clockwise_90", "clockwise_180", "counterclockwise_90"),
                                        "none"),
                                ParamSpec.integer(
                                        "segments",
                                        false,
                                        BuildSchematicParams.SEGMENTS_FROM_FILE,
                                        BuildSchematicParams.SEGMENTS_MAX,
                                        BuildSchematicParams.SEGMENTS_FROM_FILE),
                                ParamSpec.enumeration(
                                        BuildSchematicParams.WOOD, false, WoodSpecies.ALL, WoodSpecies.OAK),
                                ParamSpec.text(BuildSchematicParams.PREVIOUS, false, "")),
                        List.of("файл схемы существует"),
                        List.of("схема построена")),
                spec(
                        "build_farm",
                        "разбить грядку прямоугольником: вспахать землю у воды и посадить культуру",
                        List.of(
                                ParamSpec.enumeration("crop", true, CropKind.plotNames()),
                                ParamSpec.integer(
                                        "x1",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
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
                                ParamSpec.integer(
                                        "z2",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("есть мотыга", "рядом с прямоугольником есть вода", "есть семена культуры"),
                        List.of("земля прямоугольника вспахана там, где хватает воды", "культура посажена")));
    }

    static List<ScriptSpec> structureSpecs() {
        return List.of(
                spec(
                        "build_fence",
                        "огородить контур забором с калиткой",
                        List.of(
                                ParamSpec.text(BLOCK, true),
                                ParamSpec.text("gate", false, ""),
                                ParamSpec.textList("points", true)),
                        List.of("в инвентаре есть блоки забора и калитки"),
                        List.of("забор стоит по контуру", "в калитке проход")),
                spec(
                        "build_shelter",
                        "построить укрытие 6×6 по схеме из дешёвого материала",
                        List.of(),
                        List.of("в инвентаре полный запас булыжника или досок, полублок порога и факел"),
                        List.of(
                                "вокруг духа стоит укрытие с проёмом входа, крышей и светом",
                                "дух выходит из укрытия после стройки")),
                spec(
                        "fill_crater",
                        "засыпать воронку от взрыва ровно до уровня земли",
                        List.of(ParamSpec.text("crater", true)),
                        List.of(
                                "воронка задана id или позицией и запомнена событием взрыва",
                                "в инвентаре или ближнем сундуке есть земля"),
                        List.of("пустоты воронки засыпаны до уровня земли", "итог называет засыпанное и нехватку")));
    }

    private static ScriptSpec spec(
            String id, String summary, List<ParamSpec> params, List<String> preconditions, List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects);
    }
}
