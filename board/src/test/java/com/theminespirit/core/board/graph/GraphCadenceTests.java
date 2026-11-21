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
        assertTrue(
                new GraphCadence().due(0, CALM),
                "Первый цикл не ждёт таймер: графу нечего помнить"
                        + " Иначе тест due_FirstCycle_Always перестанет ловить эту поломку. При смене due_FirstCycle_Always проверь это утверждение.");
    }

    @Test
    void finished_AfterCycle_KeepsInterval() {
        GraphCadence cadence = new GraphCadence();
        cadence.finished(0, CALM);

        assertFalse(
                cadence.due(GraphCadence.INTERVAL_TICKS - 1, CALM),
                "Внутри интервала граф не пересчитывается: иначе цикл идёт на каждой пересборке доски"
                        + " При смене finished_AfterCycle_KeepsInterval проверь это утверждение.");
    }

    @Test
    void finished_IntervalElapsed_Due() {
        GraphCadence cadence = new GraphCadence();
        cadence.finished(0, CALM);

        assertTrue(
                cadence.due(GraphCadence.INTERVAL_TICKS, CALM),
                "Ровно через интервал граф обязан пересчитаться"
                        + " Иначе тест finished_IntervalElapsed_Due перестанет ловить эту поломку. При смене finished_IntervalElapsed_Due проверь это утверждение.");
    }

    @Test
    void finished_ClockWentBack_Due() {
        GraphCadence cadence = new GraphCadence();
        cadence.finished(600, CALM);

        assertTrue(
                cadence.due(10, CALM),
                "Откат времени (загрузка мира) не замораживает граф: цикл идёт сразу"
                        + " Иначе тест finished_ClockWentBack_Due перестанет ловить эту поломку. При смене finished_ClockWentBack_Due проверь это утверждение.");
    }

    @Test
    void due_UrgentInput_IgnoresTimer() {
        GraphCadence cadence = new GraphCadence();
        cadence.finished(0, CALM);

        Set<String> threat = GraphCadence.signals(Set.of("night", "hostile_nearby"), List.of("a"), List.of());
        Set<String> joined = GraphCadence.signals(Set.of("night"), List.of("a", "b"), List.of());
        Set<String> stuck = GraphCadence.signals(Set.of("night"), List.of("a"), List.of("a"));

        assertTrue(
                cadence.due(1, threat),
                "Угроза пересчитывает граф сразу, а не через интервал"
                        + " Иначе тест due_UrgentInput_IgnoresTimer перестанет ловить эту поломку. При смене due_UrgentInput_IgnoresTimer проверь это утверждение.");
        assertTrue(
                cadence.due(1, joined),
                "Новый дух пересчитывает граф сразу"
                        + " Иначе тест due_UrgentInput_IgnoresTimer перестанет ловить эту поломку. При смене due_UrgentInput_IgnoresTimer проверь это утверждение.");
        assertTrue(
                cadence.due(1, stuck),
                "Застрявший дух пересчитывает граф сразу"
                        + " Иначе тест due_UrgentInput_IgnoresTimer перестанет ловить эту поломку. При смене due_UrgentInput_IgnoresTimer проверь это утверждение.");
    }

    @Test
    void urgent_ThreatAndCrater_True() {
        assertTrue(
                GraphCadence.urgent("hostile_nearby"),
                "Угроза рядом — срочно"
                        + " Иначе тест urgent_ThreatAndCrater_True перестанет ловить эту поломку. При смене urgent_ThreatAndCrater_True проверь это утверждение.");
        assertTrue(
                GraphCadence.urgent("creeper_crater"),
                "Свежая воронка — срочно"
                        + " Иначе тест urgent_ThreatAndCrater_True перестанет ловить эту поломку. При смене urgent_ThreatAndCrater_True проверь это утверждение.");
        assertTrue(
                GraphCadence.urgent("life_threat"),
                "Угроза жизни — срочно"
                        + " Иначе тест urgent_ThreatAndCrater_True перестанет ловить эту поломку. При смене urgent_ThreatAndCrater_True проверь это утверждение.");
        assertFalse(
                GraphCadence.urgent("night"),
                "Ночь сама по себе не срочна: её ждёт таймер"
                        + " Иначе тест urgent_ThreatAndCrater_True перестанет ловить эту поломку. При смене urgent_ThreatAndCrater_True проверь это утверждение.");
    }

    @Test
    void signals_ThreatSpiritAndStuck_ChangeInput() {
        Set<String> threat = GraphCadence.signals(Set.of("night", "hostile_nearby"), List.of("a"), List.of());
        Set<String> joined = GraphCadence.signals(Set.of("night"), List.of("a", "b"), List.of());
        Set<String> stuck = GraphCadence.signals(Set.of("night"), List.of("a"), List.of("a"));

        assertEquals(
                GraphCadence.signals(Set.of("hostile_nearby", "night"), List.of("a"), List.of()),
                threat,
                "Порядок входа не важен: сравниваются множества"
                        + " Иначе тест signals_ThreatSpiritAndStuck_ChangeInput перестанет ловить эту поломку. При смене signals_ThreatSpiritAndStuck_ChangeInput проверь это утверждение.");
        assertFalse(
                threat.isEmpty(),
                "Угроза обязана попасть во вход: " + threat
                        + " Иначе тест signals_ThreatSpiritAndStuck_ChangeInput перестанет ловить эту поломку. При смене signals_ThreatSpiritAndStuck_ChangeInput проверь это утверждение.");
        assertFalse(
                joined.equals(CALM),
                "Новый дух меняет вход: " + joined
                        + " Иначе тест signals_ThreatSpiritAndStuck_ChangeInput перестанет ловить эту поломку. При смене signals_ThreatSpiritAndStuck_ChangeInput проверь это утверждение.");
        assertFalse(
                stuck.equals(CALM),
                "Застрявший дух меняет вход: " + stuck
                        + " Иначе тест signals_ThreatSpiritAndStuck_ChangeInput перестанет ловить эту поломку. При смене signals_ThreatSpiritAndStuck_ChangeInput проверь это утверждение.");
    }

    @Test
    void signals_NonUrgentEvent_Ignored() {
        assertEquals(
                GraphCadence.signals(Set.of(), List.of("a"), List.of()),
                GraphCadence.signals(Set.of("night", "hungry"), List.of("a"), List.of()),
                "Несрочные события не дёргают граф вне интервала"
                        + " Иначе тест signals_NonUrgentEvent_Ignored перестанет ловить эту поломку. При смене signals_NonUrgentEvent_Ignored проверь это утверждение.");
    }
}
