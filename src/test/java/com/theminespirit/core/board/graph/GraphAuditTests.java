package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphAuditTests {
    @Test
    void settles_KnownPartial_True() {
        assertTrue(
                GraphAudit.settles(state(Built.PLANNED, PlanCaseFacts.building("hut", 95, 5, Map.of(), false)), "hut"),
                "Аудит с прогрессом решает судьбу постройки: полную стройку не ставят поверх 95%");
    }

    @Test
    void settles_Unknown_True() {
        assertTrue(
                GraphAudit.settles(state(Built.PLANNED, PlanCaseFacts.building("hut", 0, 0, Map.of(), true)), "hut"),
                "Неизвестный аудит не даёт строить вслепую: работа ждёт загрузки чанка");
    }

    @Test
    void settles_NotStarted_False() {
        assertFalse(
                GraphAudit.settles(state(Built.PLANNED, PlanCaseFacts.building("hut", 0, 0, Map.of(), false)), "hut"),
                "Ноль процентов по аудиту — обычная стройка, её нельзя отменять");
    }

    @Test
    void settles_Built_False() {
        assertFalse(
                GraphAudit.settles(state(Built.BUILT, PlanCaseFacts.building("hut", 100, 0, Map.of(), false)), "hut"),
                "Готовая постройка не удерживает стройку: аудит решает только спорные случаи");
    }

    private static GraphState state(Built built, GraphFacts.Building hut) {
        return new GraphState(
                        Map.of(),
                        1,
                        Map.of("spirit-1", Map.of()),
                        Map.of("hut", built),
                        Map.of(),
                        60,
                        Set.of(),
                        Set.of())
                .withFacts(PlanCaseFacts.facts(Map.of("hut", hut), List.of(), GraphSiteFacts.Terrain.empty()));
    }
}
