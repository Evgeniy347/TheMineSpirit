package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.SupplyGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphBuildingNamesGuardTests {
    @Test
    void graphBuildings_MissingNames_AreReported() {
        assertEquals(
                List.of(),
                issues(SupplyGraph.loadDefault().buildings().values()),
                "Постройка графа без отображаемого названия: в чат уйдёт технический id вместо русского имени. "
                        + "Как надо: допишите \"name\" рядом с постройкой в graph.json или graph.d/*.json. "
                        + "Правило: у каждой постройки схемы графа есть русское отображаемое название. Нарушения");
    }

    @Test
    void issues_BlankName_IsReported() {
        assertTrue(
                issues(List.of(building("hut", ""))).stream().anyMatch(issue -> issue.contains("hut")),
                "Сторож ослеп: постройка с пустым названием обязана попасть в нарушения");
    }

    @Test
    void issues_LatinName_IsReported() {
        assertTrue(
                issues(List.of(building("hut", "Hut"))).stream().anyMatch(issue -> issue.contains("Hut")),
                "Сторож ослеп: название без кириллицы обязано попасть в нарушения");
    }

    private static SupplyGraph.Building building(String id, String name) {
        return new SupplyGraph.Building(
                id, name, BoardCase.BUILD_HOUSE, 1, 1, null, Map.of(), List.of(), List.of(), false);
    }

    private static List<String> issues(Collection<SupplyGraph.Building> buildings) {
        List<String> issues = new ArrayList<>();
        for (SupplyGraph.Building building : buildings) {
            String name = building.name().strip();
            if (name.isEmpty()) {
                issues.add("у постройки '" + building.id() + "' нет отображаемого названия");
            } else if (name.chars()
                    .noneMatch(ch -> Character.UnicodeBlock.CYRILLIC.equals(Character.UnicodeBlock.of(ch)))) {
                issues.add("название постройки '" + building.id() + "' не по-русски: '" + name + "'");
            }
        }
        return issues;
    }
}
