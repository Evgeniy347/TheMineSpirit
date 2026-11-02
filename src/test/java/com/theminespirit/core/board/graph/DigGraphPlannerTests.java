package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DigGraphPlannerTests {
    private static final GraphFacts.Position AT = new GraphFacts.Position(1, 64, 2);

    @Test
    void digOut_SealedSpirit_AddsDigAndRestore() {
        GraphPlan plan = plan("sealed");

        assertTrue(ids(plan).contains("dig_out"), "Замурованный дух получает узел dig_out");
        assertTrue(ids(plan).contains("restore_trace"), "После прохода обязателен restore_trace");
        assertFalse(ids(plan).contains("pillar"), "Для замурованного не выбирают столб");
        assertFalse(ids(plan).contains("bridge"), "Для замурованного не выбирают мост");
    }

    @Test
    void digOut_PitSpirit_AddsPillarAndRestore() {
        GraphPlan plan = plan("pit");

        assertTrue(ids(plan).contains("pillar"), "Из ямы выбирают подъём столбом");
        assertTrue(ids(plan).contains("restore_trace"), "Подъём из ямы тоже возвращает след");
        assertFalse(ids(plan).contains("dig_out"), "Для ямы копание не выбирают");
    }

    @Test
    void digOut_WaterSpirit_AddsBridgeAndRestore() {
        GraphPlan plan = plan("water");

        assertTrue(ids(plan).contains("bridge"), "Через воду выбирают мост");
        assertTrue(ids(plan).contains("restore_trace"), "Мост после прохода разбирают");
    }

    @Test
    void digOut_UntrappedSpirit_NoDigNodes() {
        GraphPlan plan = GraphWorld.settled().facts(facts(spirit(false, null))).plan();

        assertFalse(ids(plan).contains("dig_out"), "Свободный дух не копает");
        assertFalse(ids(plan).contains("restore_trace"), "Без прохода нечего восстанавливать");
    }

    @Test
    void digFacts_CauseMapping_ChoosesNode() {
        assertEquals(
                DigFacts.PILLAR_ITEM,
                DigFacts.node(DigFacts.trap(DigFacts.Cause.PIT)),
                "Причина pit ведёт к подъёму столбом");
        assertEquals(
                DigFacts.BRIDGE_ITEM,
                DigFacts.node(DigFacts.trap(DigFacts.Cause.WATER)),
                "Причина water ведёт к мосту");
        assertNull(DigFacts.trap(DigFacts.Cause.OPEN), "Свободный проход не имеет причины");
    }

    private static GraphPlan plan(String trap) {
        return GraphWorld.settled().facts(facts(spirit(true, trap))).plan();
    }

    private static Set<String> ids(GraphPlan plan) {
        return plan.tasks().stream().map(GraphPlan.Task::id).collect(Collectors.toSet());
    }

    private static GraphFacts facts(GraphFacts.Spirit spirit) {
        return new GraphFacts(
                Map.of("spirit-1", spirit),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                List.of(),
                GraphFacts.Terrain.empty(),
                new GraphFacts.StageReqs(Map.of(), Map.of()));
    }

    private static GraphFacts.Spirit spirit(boolean trapped, String trap) {
        return new GraphFacts.Spirit(
                "spirit-1", AT, 20, 20, 300, false, trapped, false, null, Map.of(), List.of(), List.of(), trap);
    }
}
