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
    void stuckSpirit_NotTrapped_GetsRescueAndRestore() {
        GraphPlan plan =
                GraphWorld.settled().facts(facts(stuck(false, true, null))).plan();

        assertTrue(ids(plan).contains("rescue_spirit"), "Застрявший дух получает узел жизни rescue_spirit");
        assertTrue(ids(plan).contains("restore_trace"), "После выхода след возвращают: restore_trace");
        assertFalse(ids(plan).contains("dig_out"), "Свободному (не замурованному) духу копание не навязывают");
        GraphPlan.Task rescue = plan.tasks().stream()
                .filter(task -> "rescue_spirit".equals(task.id()))
                .findFirst()
                .orElseThrow();
        assertTrue(rescue.feasible(), "Задание спасения выполнимо без материалов: " + rescue);
        assertTrue(rescue.priority() >= 90, "Жизнь выше обычной работы: приоритет " + rescue.priority());
    }

    @Test
    void stuckAndTrappedSpirit_RescueWork_OutranksDig() {
        GraphPlan plan =
                GraphWorld.settled().facts(facts(stuck(true, true, "sealed"))).plan();

        assertTrue(ids(plan).contains("rescue_spirit"), "Замурованный и застрявший дух получает rescue_spirit");
        assertTrue(ids(plan).contains("dig_out"), "Замуровка по-прежнему даёт dig_out");
        int rescue = priority(plan, "rescue_spirit");
        int dig = priority(plan, "dig_out");
        assertTrue(rescue > dig, "Выход жизни важнее обычного копания: rescue " + rescue + " > dig " + dig);
    }

    private static int priority(GraphPlan plan, String id) {
        return plan.tasks().stream()
                .filter(task -> id.equals(task.id()))
                .mapToInt(GraphPlan.Task::priority)
                .findFirst()
                .orElseThrow();
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
        assertNull(
                DigFacts.trap(DigFacts.Cause.WALL),
                "Стена рядом — не замуровка: дух под крышей или в пещере не копает выход, "
                        + "иначе граф каждый цикл ставит dig_out без исполнителя");
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
                GraphSiteFacts.Terrain.empty(),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none());
    }

    private static GraphFacts.Spirit spirit(boolean trapped, String trap) {
        return stuck(trapped, false, trap);
    }

    private static GraphFacts.Spirit stuck(boolean trapped, boolean stuck, String trap) {
        return new GraphFacts.Spirit(
                "spirit-1",
                AT,
                20,
                Math.max(20, 1f),
                null,
                20,
                300,
                false,
                trapped,
                stuck,
                null,
                Map.of(),
                List.of(),
                List.of(),
                trap);
    }
}
