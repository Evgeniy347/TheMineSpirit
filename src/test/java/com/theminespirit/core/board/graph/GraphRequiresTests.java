package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import org.junit.jupiter.api.Test;

class GraphRequiresTests {
    @Test
    void plan_ChestsNotBuiltHutMaterialsStocked_HutWaitsChestsRaised() {
        GraphPlan plan = GraphWorld.settled()
                .built("chests", Built.PLANNED)
                .with("oak_log", 64)
                .with("oak_planks", 64)
                .with("cobblestone", 64)
                .plan();

        assertFalse(
                plan.task("building:hut").map(GraphPlan.Task::feasible).orElse(true),
                "Хижина ставится у стола двора: без сундуков двора её не открывать, даже когда материалы есть: "
                        + plan.task("building:hut"));
        assertTrue(
                plan.priority("building:chests") > 0,
                "Хижина ждёт двор — сундуки двора получают её давление, а не стоят без задания: " + plan.tasks());
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
                "Двор готов и материалы на складе — хижина выполнима: " + plan.task("building:hut"));
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
                        + plan.task("mine_cobble"));
        assertTrue(
                plan.priority("building:mine") > 0, "Нет булыжника и шахты — шахта получает давление: " + plan.tasks());
    }

    @Test
    void parse_RequiresUnknownBuilding_Rejected() {
        String json = """
                {"items": {}, "buildings": {"hut": {"case": "BUILD_HOUSE", "requires": ["castle"]}}}
                """;

        assertThrows(
                IllegalStateException.class,
                () -> SupplyGraph.parse(json, "test.json"),
                "Ссылка на неизвестную постройку в requires — опечатка графа: задание ждало бы её вечно");
    }
}
