package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.inventory.api.JsonResource;
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
                "вход фермы обязан добавлять свои события к событиям мира: иначе граф теряет ямы и лишнее"
                        + " При смене merge_WorldFactsAndFarm_KeepBoth проверь это утверждение.");
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
                "hut", "Хижина", BoardCase.REPAIR_BUILDING, 1, 50, null, Map.of(), List.of(), List.of(), false);

        GraphEvent repaired = GraphEvent.withRepairs(Map.of("building_damaged", template), Map.of("hut", building))
                .get(GraphWorldEvents.REPAIR + "hut");

        assertInstanceOf(
                GraphEvent.class,
                repaired,
                "повреждённая постройка обязана дать событие ремонта"
                        + " Иначе тест withRepairs_RepairTask_KeepsWorldConditionsAndBuilding перестанет ловить эту поломку. При смене withRepairs_RepairTask_KeepsWorldConditionsAndBuilding проверь это утверждение.");
        assertTrue(
                repaired != null && !repaired.tasks().isEmpty(),
                "повреждённая постройка обязана дать событие ремонта"
                        + " Иначе тест withRepairs_RepairTask_KeepsWorldConditionsAndBuilding перестанет ловить эту поломку. При смене withRepairs_RepairTask_KeepsWorldConditionsAndBuilding проверь это утверждение.");
        assertEquals(
                List.of("repair:hut"),
                repaired.tasks().stream().map(GraphEvent.Task::id).toList(),
                "повреждённая постройка обязана дать событие ремонта"
                        + " Иначе тест withRepairs_RepairTask_KeepsWorldConditionsAndBuilding перестанет ловить эту поломку. При смене withRepairs_RepairTask_KeepsWorldConditionsAndBuilding проверь это утверждение.");
        GraphEvent.Task created = repaired.tasks().get(0);
        assertEquals(
                "repair:hut",
                created.id(),
                "ремонт адресуется конкретной постройке"
                        + " Иначе тест withRepairs_RepairTask_KeepsWorldConditionsAndBuilding перестанет ловить эту поломку. При смене withRepairs_RepairTask_KeepsWorldConditionsAndBuilding проверь это утверждение.");
        assertEquals(
                "hut",
                created.building(),
                "клетка ремонта — сама повреждённая постройка"
                        + " Иначе тест withRepairs_RepairTask_KeepsWorldConditionsAndBuilding перестанет ловить эту поломку. При смене withRepairs_RepairTask_KeepsWorldConditionsAndBuilding проверь это утверждение.");
        assertEquals(
                "more:damaged",
                created.world(),
                "условие мира из шаблона ремонта не теряется"
                        + " Иначе тест withRepairs_RepairTask_KeepsWorldConditionsAndBuilding перестанет ловить эту поломку. При смене withRepairs_RepairTask_KeepsWorldConditionsAndBuilding проверь это утверждение.");
        assertEquals(
                "charcoal",
                created.onlyWhenEmpty(),
                "условие пустоты из шаблона ремонта не теряется"
                        + " Иначе тест withRepairs_RepairTask_KeepsWorldConditionsAndBuilding перестанет ловить эту поломку. При смене withRepairs_RepairTask_KeepsWorldConditionsAndBuilding проверь это утверждение.");
        assertEquals(
                "hungriest",
                created.owner(),
                "владелец-исполнитель из шаблона ремонта не теряется"
                        + " Иначе тест withRepairs_RepairTask_KeepsWorldConditionsAndBuilding перестанет ловить эту поломку. При смене withRepairs_RepairTask_KeepsWorldConditionsAndBuilding проверь это утверждение.");
        assertEquals(
                85,
                created.base(),
                "вес ремонта берётся из шаблона"
                        + " Иначе тест withRepairs_RepairTask_KeepsWorldConditionsAndBuilding перестанет ловить эту поломку. При смене withRepairs_RepairTask_KeepsWorldConditionsAndBuilding проверь это утверждение.");
    }

    @Test
    void parse_AuditTask_ReadsDependenciesAndLongestPrefix() {
        String json = "{"
                + "\"events\":{\"audit\":{\"tasks\":["
                + "{\"id\":\"building\",\"case\":\"REPAIR_BUILDING\",\"base\":55},"
                + "{\"id\":\"building:storage\",\"case\":\"REPAIR_BUILDING\",\"base\":68,"
                + "\"after\":\"audit:street:s-1\",\"requires\":[\"audit:ground:pit\"]}]}}}";

        GraphEvent event =
                GraphEvent.parse(JsonResource.parse(json, "audit-test")).get("audit");
        GraphEvent.Task chosen = GraphEvent.find(event, "building:storage_3");

        assertEquals(
                "building:storage",
                chosen.id(),
                "Префиксный поиск выбирает самый длинный шаблон:"
                        + " иначе склад получит общий вес постройки вместо своего места в лестнице. При смене поиска"
                        + " перепроверь GraphEvent.find.");
        assertEquals(
                "audit:street:s-1",
                chosen.after(),
                "Предшественник читается из данных: иначе улица"
                        + " и склад смогут выполняться одновременно. При смене формата перепроверь GraphEvent.parse.");
        assertEquals(
                List.of("audit:ground:pit"),
                chosen.requires(),
                "Список обязательных групп читается из данных: иначе зависимость от ямы потеряется, а склад"
                        + " начнёт строиться до расчистки. При смене формата перепроверь texts и GraphEvent.parse.");
        assertEquals(
                "building",
                GraphEvent.find(event, "building:other").id(),
                "Общий шаблон остаётся"
                        + " запасным для построек без специального префикса: иначе аудит не найдёт кейс. При смене"
                        + " поиска перепроверь GraphEvent.find.");
    }
}
