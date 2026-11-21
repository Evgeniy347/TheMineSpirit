package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import org.junit.jupiter.api.Test;

class GraphRequiresTests {
    @Test
    void plan_ClearingNotBuiltHutMaterialsStocked_HutWaitsClearingRaised() {
        GraphWorld world = GraphWorld.settled()
                .built("clearing", Built.PLANNED)
                .with("oak_log", 64)
                .with("oak_planks", 64)
                .with("cobblestone", 64);
        GraphPlan before = world.plan();
        GraphPlan after = world.built("clearing", Built.BUILT).plan();

        assertFalse(
                before.task("building:hut").map(GraphPlan.Task::feasible).orElse(true),
                "Хижина ждёт выровненной площадки, а не сундуков: стройка этапа идёт вместе с двором, как в цепочке: "
                        + before.task("building:hut")
                        + " Иначе тест plan_ClearingNotBuiltHutMaterialsStocked_HutWaitsClearingRaised перестанет ловить эту поломку. При смене plan_ClearingNotBuiltHutMaterialsStocked_HutWaitsClearingRaised проверь это утверждение.");
        assertTrue(
                after.task("building:hut").map(GraphPlan.Task::feasible).orElse(false),
                "Площадка выровнена и материалы на складе — хижина выполнима: " + after.task("building:hut")
                        + " Иначе тест plan_ClearingNotBuiltHutMaterialsStocked_HutWaitsClearingRaised перестанет ловить эту поломку. При смене plan_ClearingNotBuiltHutMaterialsStocked_HutWaitsClearingRaised проверь это утверждение.");
    }

    @Test
    void plan_ChestsBuiltHutMaterialsStocked_HutFeasible() {
        GraphPlan plan = GraphWorld.settled()
                .with("oak_log", 64)
                .with("oak_planks", 64)
                .with("cobblestone", 64)
                .plan();

        assertTrue(
                plan.task("building:hut").map(GraphPlan.Task::feasible).orElse(false),
                "Двор готов и материалы на складе — хижина выполнима: " + plan.task("building:hut")
                        + " Иначе тест plan_ChestsBuiltHutMaterialsStocked_HutFeasible перестанет ловить эту поломку. При смене plan_ChestsBuiltHutMaterialsStocked_HutFeasible проверь это утверждение.");
    }

    @Test
    void plan_HutNotBuiltOrchardMaterialsStocked_OrchardWaitsHutRaised() {
        GraphWorld world = GraphWorld.settled()
                .built("hut", Built.PLANNED)
                .with("oak_sapling", 4)
                .with("torch", 8)
                .with("dirt", 16);
        GraphPlan before = world.plan();
        GraphPlan after = world.built("hut", Built.BUILT).plan();

        assertFalse(
                before.task("building:orchard_oak")
                        .map(GraphPlan.Task::feasible)
                        .orElse(true),
                "Сад идёт после хижины: пока её нет, его цветы и дорожки не отвлекают духов от ограды и дома: "
                        + before.task("building:orchard_oak")
                        + " Иначе тест plan_HutNotBuiltOrchardMaterialsStocked_OrchardWaitsHutRaised перестанет ловить эту поломку. При смене plan_HutNotBuiltOrchardMaterialsStocked_OrchardWaitsHutRaised проверь это утверждение.");
        assertTrue(
                after.task("building:orchard_oak").map(GraphPlan.Task::feasible).orElse(false),
                "Хижина построена и материалы сада на складе — сад выполним: " + after.task("building:orchard_oak")
                        + " Иначе тест plan_HutNotBuiltOrchardMaterialsStocked_OrchardWaitsHutRaised перестанет ловить эту поломку. При смене plan_HutNotBuiltOrchardMaterialsStocked_OrchardWaitsHutRaised проверь это утверждение.");
    }

    @Test
    void plan_MineNotBuiltNoCobble_CobbleWaitsMineRaised() {
        GraphPlan plan = GraphWorld.settled()
                .built("mine", Built.PLANNED)
                .with("cobblestone", 0)
                .plan();

        assertFalse(
                plan.task("mine_cobble").map(GraphPlan.Task::feasible).orElse(true),
                "Булыжник добывается в шахте: без неё копать камень на участке нельзя (ямы под постройками): "
                        + plan.task("mine_cobble")
                        + " Иначе тест plan_MineNotBuiltNoCobble_CobbleWaitsMineRaised перестанет ловить эту поломку. При смене plan_MineNotBuiltNoCobble_CobbleWaitsMineRaised проверь это утверждение.");
        assertTrue(
                plan.raised("building:mine"),
                "Нет булыжника и шахты — шахта получает давление: " + plan.tasks()
                        + " Иначе тест plan_MineNotBuiltNoCobble_CobbleWaitsMineRaised перестанет ловить эту поломку. При смене plan_MineNotBuiltNoCobble_CobbleWaitsMineRaised проверь это утверждение.");
    }

    @Test
    void parse_RequiresUnknownBuilding_Rejected() {
        String json = """
                {"items": {}, "buildings": {"hut": {"case": "BUILD_HOUSE", "requires": ["castle"]}}}
                """;

        assertThrows(
                IllegalStateException.class,
                () -> SupplyGraph.parse(json, "test.json"),
                "Ссылка на неизвестную постройку в requires — опечатка графа: задание ждало бы её вечно"
                        + " Иначе тест parse_RequiresUnknownBuilding_Rejected перестанет ловить эту поломку. При смене parse_RequiresUnknownBuilding_Rejected проверь это утверждение.");
    }
}
