package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphCadenceTests {
    private static final Set<String> CALM = GraphCadence.signals(Set.of("night"), List.of("a"), List.of());

    @Test
    void due_FirstCycle_Always() {
        assertTrue(new GraphCadence().due(0, CALM), "Первый цикл не ждёт таймер: графу нечего помнить");
    }

    @Test
    void finished_AfterCycle_KeepsInterval() {
        GraphCadence cadence = new GraphCadence();
        cadence.finished(0, CALM);

        assertFalse(
                cadence.due(GraphCadence.INTERVAL_TICKS - 1, CALM),
                "Внутри интервала граф не пересчитывается: иначе цикл идёт на каждой пересборке доски");
    }

    @Test
    void finished_IntervalElapsed_Due() {
        GraphCadence cadence = new GraphCadence();
        cadence.finished(0, CALM);

        assertTrue(cadence.due(GraphCadence.INTERVAL_TICKS, CALM), "Ровно через интервал граф обязан пересчитаться");
    }

    @Test
    void finished_ClockWentBack_Due() {
        GraphCadence cadence = new GraphCadence();
        cadence.finished(600, CALM);

        assertTrue(cadence.due(10, CALM), "Откат времени (загрузка мира) не замораживает граф: цикл идёт сразу");
    }

    @Test
    void due_UrgentInput_IgnoresTimer() {
        GraphCadence cadence = new GraphCadence();
        cadence.finished(0, CALM);

        Set<String> threat = GraphCadence.signals(Set.of("night", "hostile_nearby"), List.of("a"), List.of());
        Set<String> joined = GraphCadence.signals(Set.of("night"), List.of("a", "b"), List.of());
        Set<String> stuck = GraphCadence.signals(Set.of("night"), List.of("a"), List.of("a"));

        assertTrue(cadence.due(1, threat), "Угроза пересчитывает граф сразу, а не через интервал");
        assertTrue(cadence.due(1, joined), "Новый дух пересчитывает граф сразу");
        assertTrue(cadence.due(1, stuck), "Застрявший дух пересчитывает граф сразу");
    }

    @Test
    void urgent_ThreatAndCrater_True() {
        assertTrue(GraphCadence.urgent("hostile_nearby"), "Угроза рядом — срочно");
        assertTrue(GraphCadence.urgent("creeper_crater"), "Свежая воронка — срочно");
        assertTrue(GraphCadence.urgent("life_threat"), "Угроза жизни — срочно");
        assertFalse(GraphCadence.urgent("night"), "Ночь сама по себе не срочна: её ждёт таймер");
    }

    @Test
    void signals_ThreatSpiritAndStuck_ChangeInput() {
        Set<String> threat = GraphCadence.signals(Set.of("night", "hostile_nearby"), List.of("a"), List.of());
        Set<String> joined = GraphCadence.signals(Set.of("night"), List.of("a", "b"), List.of());
        Set<String> stuck = GraphCadence.signals(Set.of("night"), List.of("a"), List.of("a"));

        assertEquals(
                GraphCadence.signals(Set.of("hostile_nearby", "night"), List.of("a"), List.of()),
                threat,
                "Порядок входа не важен: сравниваются множества");
        assertFalse(threat.isEmpty(), "Угроза обязана попасть во вход: " + threat);
        assertFalse(joined.equals(CALM), "Новый дух меняет вход: " + joined);
        assertFalse(stuck.equals(CALM), "Застрявший дух меняет вход: " + stuck);
    }

    @Test
    void signals_NonUrgentEvent_Ignored() {
        assertEquals(
                GraphCadence.signals(Set.of(), List.of("a"), List.of()),
                GraphCadence.signals(Set.of("night", "hungry"), List.of("a"), List.of()),
                "Несрочные события не дёргают граф вне интервала");
    }
}
