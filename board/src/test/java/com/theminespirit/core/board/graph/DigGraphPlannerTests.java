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
    void planBreach_FreeSpirit_GetsPlanBreachTicket() {
        GraphPlan plan = GraphWorld.settled()
                .facts(facts(spirit(false, null), List.of(new GraphFacts.Position(3, 64, 4))))
                .plan();

        assertTrue(
                ids(plan).contains("event:plan_breach:plan_breach"),
                "Открытый пролом плана обязан дать узел plan_breach даже свободному духу: заделку клеток "
                        + "вне досягаемости духа ставит доска, а не тишина; иначе дыра в постройке остаётся навсегда"
                        + " При смене planBreach_FreeSpirit_GetsPlanBreachTicket проверь это утверждение: "
                        + ids(plan));
    }

    @Test
    void digOut_SealedSpirit_AddsDigAndRestore() {
        GraphPlan plan = plan("sealed");

        assertTrue(
                ids(plan).contains("dig_out"),
                "Замурованный дух получает узел dig_out"
                        + " Иначе тест digOut_SealedSpirit_AddsDigAndRestore перестанет ловить эту поломку. При смене digOut_SealedSpirit_AddsDigAndRestore проверь это утверждение.");
        assertFalse(
                ids(plan).contains("pillar"),
                "Для замурованного не выбирают столб"
                        + " Иначе тест digOut_SealedSpirit_AddsDigAndRestore перестанет ловить эту поломку. При смене digOut_SealedSpirit_AddsDigAndRestore проверь это утверждение.");
        assertFalse(
                ids(plan).contains("bridge"),
                "Для замурованного не выбирают мост"
                        + " Иначе тест digOut_SealedSpirit_AddsDigAndRestore перестанет ловить эту поломку. При смене digOut_SealedSpirit_AddsDigAndRestore проверь это утверждение.");
    }

    @Test
    void digOut_PitSpirit_AddsPillarAndRestore() {
        GraphPlan plan = plan("pit");

        assertTrue(
                ids(plan).contains("pillar"),
                "Из ямы выбирают подъём столбом"
                        + " Иначе тест digOut_PitSpirit_AddsPillarAndRestore перестанет ловить эту поломку. При смене digOut_PitSpirit_AddsPillarAndRestore проверь это утверждение.");
        assertFalse(
                ids(plan).contains("dig_out"),
                "Для ямы копание не выбирают"
                        + " Иначе тест digOut_PitSpirit_AddsPillarAndRestore перестанет ловить эту поломку. При смене digOut_PitSpirit_AddsPillarAndRestore проверь это утверждение.");
    }

    @Test
    void digOut_WaterSpirit_AddsBridgeAndRestore() {
        GraphPlan plan = plan("water");

        assertTrue(
                ids(plan).contains("bridge"),
                "Через воду выбирают мост"
                        + " Иначе тест digOut_WaterSpirit_AddsBridgeAndRestore перестанет ловить эту поломку. При смене digOut_WaterSpirit_AddsBridgeAndRestore проверь это утверждение.");
        assertFalse(
                ids(plan).contains("dig_out"),
                "Свободный дух не копает"
                        + " Иначе тест digOut_UntrappedSpirit_NoDigNodes перестанет ловить эту поломку. При смене digOut_UntrappedSpirit_NoDigNodes проверь это утверждение.");
    }

    @Test
    void stuckSpirit_NotTrapped_GetsRescueAndRestore() {
        GraphPlan plan =
                GraphWorld.settled().facts(facts(stuck(false, true, null))).plan();

        assertTrue(
                ids(plan).contains("rescue_spirit"),
                "Застрявший дух получает узел жизни rescue_spirit"
                        + " Иначе тест stuckSpirit_NotTrapped_GetsRescueAndRestore перестанет ловить эту поломку. При смене stuckSpirit_NotTrapped_GetsRescueAndRestore проверь это утверждение.");
        assertFalse(
                ids(plan).contains("dig_out"),
                "Свободному (не замурованному) духу копание не навязывают"
                        + " Иначе тест stuckSpirit_NotTrapped_GetsRescueAndRestore перестанет ловить эту поломку. При смене stuckSpirit_NotTrapped_GetsRescueAndRestore проверь это утверждение.");
        GraphPlan.Task rescue = plan.tasks().stream()
                .filter(task -> "rescue_spirit".equals(task.id()))
                .findFirst()
                .orElseThrow();
        assertTrue(
                rescue.feasible(),
                "Задание спасения выполнимо без материалов: " + rescue
                        + " Иначе тест stuckSpirit_NotTrapped_GetsRescueAndRestore перестанет ловить эту поломку. При смене stuckSpirit_NotTrapped_GetsRescueAndRestore проверь это утверждение.");
        assertEquals(
                List.of(),
                CrossScene.workNotBehind(plan, rescue),
                "Жизнь выше обычной работы: " + plan.tasks()
                        + " Иначе тест stuckSpirit_NotTrapped_GetsRescueAndRestore перестанет ловить эту поломку. При смене stuckSpirit_NotTrapped_GetsRescueAndRestore проверь это утверждение.");
    }

    @Test
    void stuckAndTrappedSpirit_RescueWork_OutranksDig() {
        GraphPlan plan =
                GraphWorld.settled().facts(facts(stuck(true, true, "sealed"))).plan();

        assertTrue(
                ids(plan).contains("rescue_spirit"),
                "Замурованный и застрявший дух получает rescue_spirit"
                        + " Иначе тест stuckAndTrappedSpirit_RescueWork_OutranksDig перестанет ловить эту поломку. При смене stuckAndTrappedSpirit_RescueWork_OutranksDig проверь это утверждение.");
        assertTrue(
                ids(plan).contains("dig_out"),
                "Замуровка по-прежнему даёт dig_out"
                        + " Иначе тест stuckAndTrappedSpirit_RescueWork_OutranksDig перестанет ловить эту поломку. При смене stuckAndTrappedSpirit_RescueWork_OutranksDig проверь это утверждение.");
        int rescue = priority(plan, "rescue_spirit");
        int dig = priority(plan, "dig_out");
        assertTrue(
                rescue > dig,
                "Выход жизни важнее обычного копания: rescue " + rescue + " > dig " + dig
                        + " Иначе тест stuckAndTrappedSpirit_RescueWork_OutranksDig перестанет ловить эту поломку. При смене stuckAndTrappedSpirit_RescueWork_OutranksDig проверь это утверждение.");
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
                "Причина pit ведёт к подъёму столбом"
                        + " Иначе тест digFacts_CauseMapping_ChoosesNode перестанет ловить эту поломку. При смене digFacts_CauseMapping_ChoosesNode проверь это утверждение.");
        assertEquals(
                DigFacts.BRIDGE_ITEM,
                DigFacts.node(DigFacts.trap(DigFacts.Cause.WATER)),
                "Причина water ведёт к мосту"
                        + " Иначе тест digFacts_CauseMapping_ChoosesNode перестанет ловить эту поломку. При смене digFacts_CauseMapping_ChoosesNode проверь это утверждение.");
        assertNull(
                DigFacts.trap(DigFacts.Cause.OPEN),
                "Свободный проход не имеет причины"
                        + " Иначе тест digFacts_CauseMapping_ChoosesNode перестанет ловить эту поломку. При смене digFacts_CauseMapping_ChoosesNode проверь это утверждение.");
        assertNull(
                DigFacts.trap(DigFacts.Cause.WALL),
                "Стена рядом — не замуровка: дух под крышей или в пещере не копает выход, "
                        + "иначе граф каждый цикл ставит dig_out без исполнителя"
                        + " При смене digFacts_CauseMapping_ChoosesNode проверь это утверждение.");
    }

    private static GraphPlan plan(String trap) {
        return GraphWorld.settled().facts(facts(spirit(true, trap))).plan();
    }

    private static Set<String> ids(GraphPlan plan) {
        return plan.tasks().stream().map(GraphPlan.Task::id).collect(Collectors.toSet());
    }

    private static GraphFacts facts(GraphFacts.Spirit spirit) {
        return facts(spirit, List.of());
    }

    private static GraphFacts facts(GraphFacts.Spirit spirit, List<GraphFacts.Position> breaches) {
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
                GraphFeedback.none(),
                List.of(),
                breaches,
                List.of());
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
