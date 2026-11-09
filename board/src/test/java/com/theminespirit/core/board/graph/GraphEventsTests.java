package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphEventsTests {
    @Test
    void merge_WorldFactsAndFarm_KeepBoth() {
        Set<String> merged = GraphEvents.merge(Set.of("backfill", "demolish_stray"), Set.of("crop_ready"));

        assertEquals(
                Set.of("backfill", "demolish_stray", "crop_ready"),
                merged,
                "вход фермы обязан добавлять свои события к событиям мира: иначе граф теряет ямы и лишнее");
    }

    @Test
    void withRepairs_RepairTask_KeepsWorldConditionsAndBuilding() {
        GraphEvent.Task task = new GraphEvent.Task(
                "repair_building",
                BoardCase.REPAIR_BUILDING,
                85,
                Map.of("#planks", 8),
                null,
                "more:damaged",
                "charcoal",
                "hungriest",
                true);
        GraphEvent template = new GraphEvent("building_damaged", List.of(task), Map.of(), List.of(), false);
        SupplyGraph.Building building = new SupplyGraph.Building(
                "hut", BoardCase.REPAIR_BUILDING, 1, 50, null, Map.of(), List.of(), List.of(), false);

        GraphEvent repaired = GraphEvent.withRepairs(Map.of("building_damaged", template), Map.of("hut", building))
                .get(GraphWorldEvents.REPAIR + "hut");

        assertEquals(
                List.of("repair:hut"),
                repaired.tasks().stream().map(GraphEvent.Task::id).toList(),
                "повреждённая постройка обязана дать событие ремонта");
        GraphEvent.Task created = repaired.tasks().get(0);
        assertEquals("repair:hut", created.id(), "ремонт адресуется конкретной постройке");
        assertEquals("hut", created.building(), "клетка ремонта — сама повреждённая постройка");
        assertEquals("more:damaged", created.world(), "условие мира из шаблона ремонта не теряется");
        assertEquals("charcoal", created.onlyWhenEmpty(), "условие пустоты из шаблона ремонта не теряется");
        assertEquals("hungriest", created.owner(), "владелец-исполнитель из шаблона ремонта не теряется");
        assertEquals(85, created.base(), "вес ремонта берётся из шаблона");
    }
}
