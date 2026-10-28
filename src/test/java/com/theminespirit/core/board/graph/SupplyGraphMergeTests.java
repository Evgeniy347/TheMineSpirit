package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SupplyGraphMergeTests {
    private static final String MANUAL = """
            {
              "items": {
                "stick": {"xmin": 8, "base": 40},
                "coal": {"xmin": 8, "base": 40}
              },
              "buildings": {},
              "groups": {"#planks": ["oak_planks"]},
              "ways": [
                {
                  "id": "manual_stick",
                  "case": "GATHER_MATERIALS",
                  "makes": "stick",
                  "yield": 4,
                  "weight": 1.0,
                  "consumes": {"#planks": 2}
                }
              ]
            }
            """;
    private static final String GENERATED = """
            {
              "items": {
                "coal": {"xmin": 0, "base": 20},
                "iron_block": {"xmin": 0, "base": 20, "stage": 2},
                "iron_ingot": {"xmin": 0, "base": 20, "stage": 2}
              },
              "buildings": {
                "blast_furnace": {
                  "case": "GATHER_MATERIALS",
                  "stage": 1,
                  "base": 0,
                  "optional": true
                }
              },
              "groups": {"#planks": ["oak_planks", "birch_planks"]},
              "ways": [
                {
                  "id": "auto_stick",
                  "case": "GATHER_MATERIALS",
                  "makes": "stick",
                  "yield": 4,
                  "weight": 1.0,
                  "consumes": {"#planks": 2}
                },
                {
                  "id": "auto_iron_block",
                  "case": "GATHER_MATERIALS",
                  "makes": "iron_block",
                  "yield": 1,
                  "weight": 1.0,
                  "consumes": {"iron_ingot": 9}
                },
                {
                  "id": "auto_iron_ingot",
                  "case": "GATHER_MATERIALS",
                  "makes": "iron_ingot",
                  "yield": 1,
                  "weight": 0.1,
                  "consumes": {"iron_block": 1},
                  "building": "blast_furnace"
                }
              ]
            }
            """;

    private static final String DUPLICATE_IDS = """
            {
              "items": {"stick": {"xmin": 0, "base": 20}},
              "ways": [
                {"id": "same", "case": "GATHER_MATERIALS", "makes": "stick", "yield": 1, "weight": 1.0},
                {"id": "same", "case": "GATHER_MATERIALS", "makes": "stick", "yield": 2, "weight": 1.0}
              ]
            }
            """;

    @Test
    void parse_DuplicateWayIds_Rejected() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> SupplyGraph.parse(DUPLICATE_IDS, "duplicate.json"),
                "Повтор id путей обязан отклоняться: иначе причины заданий и дедупликация доски путаются");

        assertTrue(
                error.getMessage().contains("same"),
                "Сторож обязан назвать повторяющийся id пути: " + error.getMessage());
    }

    @Test
    void merge_ManualAndGenerated_ManualWinsForSameMakes() {
        SupplyGraph graph = SupplyGraph.merge(MANUAL, "manual.json", GENERATED, "generated.json");

        assertEquals(
                List.of("manual_stick"),
                graph.producers("stick").stream().map(SupplyGraph.Way::id).toList(),
                "Ручной путь с тем же makes главнее: сгенерированный путь для палок отбрасывается");
        assertTrue(graph.way("auto_iron_block").isPresent(), "Путь для предмета без ручных путей добавляется");
        assertEquals(40, graph.items().get("coal").base(), "Ручные данные предмета главнее сгенерированных");
        assertEquals(2, graph.items().get("iron_block").stage(), "Новый предмет получает этап из генератора");
        assertEquals(List.of("oak_planks"), graph.groups().get("#planks"), "Ручная группа главнее сгенерированной");
        assertTrue(graph.buildings().containsKey("blast_furnace"), "Станция из генератора добавляется в граф");
    }

    @Test
    void merge_GeneratedOnlyItem_IsAddedToGraph() {
        SupplyGraph graph = SupplyGraph.merge(MANUAL, "manual.json", GENERATED, "generated.json");

        assertFalse(
                graph.items().containsKey("oak_planks"),
                "Член группы без собственного рецепта не становится предметом-заглушкой");
        assertTrue(graph.way("auto_iron_ingot").isPresent(), "Сгенерированный путь без ручного аналога остаётся");
        assertEquals(0, graph.items().get("iron_ingot").xmin(), "Заглушка нового предмета не создаёт спрос");
    }
}
