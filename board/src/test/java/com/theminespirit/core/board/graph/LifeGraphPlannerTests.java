package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LifeGraphPlannerTests {
    @Test
    void fragment_LifeThreat_LivesInOwnFileWithBand() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        GraphEvent life =
                graph.events().getOrDefault("life_threat", new GraphEvent("", List.of(), Map.of(), List.of()));

        assertEquals("life_threat", life.name(), "Фрагмент life.json обязан добавить событие life_threat");
        assertEquals(20, life.band(), "Жизнь идёт отдельной полосой выше обычного потолка");
        assertTrue(
                life.tasks().stream()
                        .map(GraphEvent.Task::id)
                        .toList()
                        .containsAll(List.of("defend", "retreat", "arm", "heal", "eat", "sleep")),
                "В событии жизни есть пути защищаться, отходить, вооружаться, лечиться, есть и спать: " + life.tasks());
    }

    @Test
    void plan_LifeThreat_DefendAboveMaxPriority() {
        GraphWorld world = GraphWorld.settled().event("life_threat");
        GraphPlan plan = world.plan();

        int max = GraphWorld.GRAPH.maxPriority();
        for (String id : List.of(
                "event:life_threat:defend",
                "event:life_threat:retreat",
                "event:life_threat:arm",
                "event:life_threat:heal",
                "event:life_threat:eat",
                "event:life_threat:sleep")) {
            assertTrue(plan.task(id).isPresent(), "Путь жизни " + id + " обязан быть заданием: " + plan.tasks());
            assertTrue(
                    plan.priority(id) > max,
                    "Защита жизни выше обычного потолка " + max + ", а не вровень: " + id + "=" + plan.priority(id));
        }
        assertTrue(
                plan.priority("event:life_threat:defend") >= plan.priority("event:life_threat:sleep"),
                "Защита важнее сна внутри полосы жизни: " + plan.tasks());
        assertTrue(
                plan.tasks().stream()
                        .filter(task -> !task.id().startsWith("event:life_"))
                        .allMatch(task -> task.priority() <= max),
                "Полоса жизни не поднимает обычные работы выше потолка: " + plan.tasks());
    }

    @Test
    void plan_ThreatToSpirit_ProtectSpiritAboveMax() {
        GraphPlan plan = GraphWorld.settled().event("life_threat_spirit").plan();

        assertTrue(
                plan.task("event:life_threat_spirit:protect_spirit").isPresent(),
                "Есть угроза духу — защита духа: " + plan.tasks());
        assertTrue(
                plan.priority("event:life_threat_spirit:protect_spirit") > GraphWorld.GRAPH.maxPriority(),
                "Защита духа выше обычного потолка: " + plan.priority("event:life_threat_spirit:protect_spirit"));
    }

    @Test
    void plan_ThreatToPlayer_ProtectPlayerAboveMax() {
        GraphPlan plan = GraphWorld.settled().event("life_threat_player").plan();

        assertTrue(
                plan.task("event:life_threat_player:protect_player").isPresent(),
                "Есть угроза игроку — защита игрока: " + plan.tasks());
        assertTrue(
                plan.priority("event:life_threat_player:protect_player") > GraphWorld.GRAPH.maxPriority(),
                "Защита игрока выше обычного потолка: " + plan.priority("event:life_threat_player:protect_player"));
    }

    @Test
    void bandPriority_AnyScore_StaysAboveMaxAndKeepsOrder() {
        assertEquals(0, GraphFlow.bandPriority(0, 100, 20), "Нет потока — нет задания");
        assertTrue(GraphFlow.bandPriority(1, 100, 20) > 100, "Даже слабый поток жизни выше обычного потолка");
        assertEquals(
                120,
                GraphFlow.bandPriority(130, 100, 20),
                "Сильный поток жизни держится в полосе, а не растёт бесконечно");
        assertTrue(
                GraphFlow.bandPriority(110, 100, 20) > GraphFlow.bandPriority(101, 100, 20),
                "Внутри полосы жизни порядок сохраняется");
    }
}
