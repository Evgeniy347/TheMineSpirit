package com.theminespirit.core.script;

import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.perception.MemoryType;
import java.util.List;

final class SenseScriptSpecs {
    private SenseScriptSpecs() {}

    private static final String RADIUS = "radius";
    private static final String FILTER = "filter";
    private static final String SPECIES = "species";

    static List<ScriptSpec> perceptionSpecs() {
        return List.of(
                spec(
                        "scan",
                        "просканировать мир вокруг духа",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS, false, ScanRadius.MIN, ScanRadius.MAX, ScanRadius.DEFAULT.value()),
                                ParamSpec.textList(FILTER, false)),
                        List.of(),
                        List.of("обновлена карта блоков, сущностей и контейнеров")),
                spec(
                        "scan_blocks",
                        "просканировать только блоки вокруг духа",
                        List.of(CommonScriptSpecs.perceptionRadius(), ParamSpec.textList(FILTER, false)),
                        List.of(),
                        List.of("блоки по фильтру в радиусе найдены, сущности и контейнеры не собирались")),
                spec(
                        "scan_entities",
                        "просканировать только сущности вокруг духа",
                        List.of(CommonScriptSpecs.perceptionRadius(), ParamSpec.textList(FILTER, false)),
                        List.of(),
                        List.of("сущности по фильтру в радиусе найдены, блоки и контейнеры не собирались")),
                spec(
                        "sense_hazard",
                        "узнать об опасностях вокруг духа: лава, вода, обрыв",
                        List.of(CommonScriptSpecs.perceptionRadius()),
                        List.of(),
                        List.of("факты об опасностях в радиусе найдены, дух не двигался")),
                spec(
                        "locate_block",
                        "найти ближайший блок по фильтру",
                        List.of(ParamSpec.textList(FILTER, true), CommonScriptSpecs.perceptionRadius()),
                        List.of(),
                        List.of("позиция ближайшего блока найдена или сообщено, что блока нет")),
                spec(
                        "locate_tree",
                        "найти ближайшее дерево заданной породы",
                        List.of(
                                ParamSpec.enumeration(SPECIES, false, TreeSpecies.names(), TreeSpecies.ANY),
                                CommonScriptSpecs.perceptionRadius()),
                        List.of(),
                        List.of("позиция ближайшего ствола найдена или сообщено, что дерева нет")),
                spec(
                        "locate_container",
                        "найти ближайший контейнер",
                        List.of(CommonScriptSpecs.perceptionRadius()),
                        List.of(),
                        List.of("позиция ближайшего контейнера найдена или сообщено, что контейнера нет")),
                spec(
                        "look_at",
                        "посмотреть на клетку: блок, сущность, контейнер",
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
                        List.of(),
                        List.of("описание клетки получено, чанк не загружался")));
    }

    static List<ScriptSpec> memorySpecs() {
        return List.of(
                spec(
                        "remember",
                        "запомнить точку под именем",
                        List.of(
                                ParamSpec.text("name", true),
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
                                ParamSpec.enumeration("type", true, MemoryType.ids())),
                        List.of("в памяти есть место для точки этого типа"),
                        List.of("точка записана в память духа и переживёт перезапуск")),
                spec(
                        "recall",
                        "вспомнить точку по имени",
                        List.of(ParamSpec.text("name", true)),
                        List.of(),
                        List.of("точка найдена в памяти или сообщено, что её нет")),
                spec(
                        "recall_all",
                        "вспомнить все точки типа",
                        List.of(ParamSpec.enumeration("type", true, MemoryType.ids())),
                        List.of(),
                        List.of("список точек типа получен")),
                spec(
                        "forget",
                        "забыть точку по имени",
                        List.of(ParamSpec.text("name", true)),
                        List.of(),
                        List.of("точки с таким именем в памяти нет")));
    }

    private static ScriptSpec spec(
            String id, String summary, List<ParamSpec> params, List<String> preconditions, List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects);
    }
}
