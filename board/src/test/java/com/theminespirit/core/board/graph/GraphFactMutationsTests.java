package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GraphFactMutationsTests {
    @Test
    void exported_FactsPresent_CoverLotReadyAndWrong() {
        List<String> names = GraphMutations.exported().stream()
                .map(GraphMutations.Mutation::name)
                .toList();

        assertTrue(
                names.contains("hut.lotReady=false"), "Генератор 200 состояний обязан проверять факт «лот не готов»");
        assertTrue(names.contains("hut.wrong=1"), "Генератор 200 состояний обязан проверять факт «чужие клетки»");
    }

    @Test
    void lotReadyMutation_LotNotReady_SettlesAudit() {
        GraphWorld world = GraphWorld.settled().lotReady("hut", false);

        assertTrue(
                GraphAudit.settles(world.state(), "hut"),
                "Неготовый лот — работа выравнивания, а не полная стройка: граф обязан это видеть");
        assertFalse(
                GraphAudit.settles(GraphWorld.settled().state(), "hut"),
                "Без факта постройки аудит не закрывает стройку: факт приходит из мира, а не выдумывается");
    }

    @Test
    void wrongMutation_WrongCells_AddRepairEvent() {
        GraphWorld world = GraphWorld.settled().wrong("hut", 1);

        assertTrue(
                world.state().events().contains("repair_hut"),
                "Чужие клетки в плане поднимают ремонт: событие приходит из фактов аудита");
    }
}
