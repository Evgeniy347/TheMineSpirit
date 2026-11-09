package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageTwo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphDemandMechanismTests {
    private static final Map<String, GraphFacts.Spirit> TWO = named(spirit("spirit-1"), spirit("spirit-2"));

    @Test
    void demand_RepeatedTenTimes_OneRequestNoMoreThanTarget() {
        GraphWorld world = stageTwo().with("cobblestone", 0);
        int previous = -1;
        for (int run = 1; run <= 10; run++) {
            GraphTickets.Result result =
                    GraphTickets.of(world.graph, world.state().withFacts(facts(TWO)), plan(world, facts(TWO)));
            List<GraphTickets.Ticket> stone = result.post().stream()
                    .filter(ticket -> "cobblestone".equals(ticket.task().makes()))
                    .toList();
            assertEquals(
                    1,
                    stone.size(),
                    "Прогон " + run + ": нехватка камня — одна заявка, а не по заявке на духа: " + result.post());
            assertTrue(
                    stone.get(0).count() <= GraphTickets.COUNT_MAX,
                    "Прогон " + run + ": заявка не больше цели/потолка: " + stone.get(0));
            if (previous >= 0) {
                assertEquals(previous, stone.get(0).count(), "Прогон " + run + ": повторный спрос не растит заявку");
            }
            previous = stone.get(0).count();
        }

        world.with("cobblestone", 64);
        GraphPlan full = plan(world, facts(TWO));
        assertTrue(
                full.task("mine_cobble").isEmpty(), "Цель достигнута — добыча камня не заказывается: " + full.tasks());
        GraphTickets.Result closed = GraphTickets.of(world.graph, world.state().withFacts(facts(TWO)), full);
        assertTrue(
                closed.post().stream()
                        .noneMatch(ticket -> "cobblestone".equals(ticket.task().makes())),
                "Цель достигнута — заявок на камень нет: " + closed.post());
    }

    @Test
    void demand_Disappeared_PriorityReturnsAndComesBack() {
        GraphWorld world = stageTwo().with("cobblestone", 0);
        GraphPlan lacking = plan(world, facts(TWO));
        int raised = lacking.priority("mine_cobble");
        assertTrue(raised > 0, "Нехватка поднимает источник камня: " + lacking.tasks());

        world.with("cobblestone", 64);
        GraphPlan stocked = plan(world, facts(TWO));
        assertEquals(
                -1,
                stocked.priority("mine_cobble"),
                "Нехватка исчезла — приоритет источника вернулся к нулю: " + stocked.tasks());

        world.with("cobblestone", 0);
        GraphPlan again = plan(world, facts(TWO));
        assertEquals(
                raised,
                again.priority("mine_cobble"),
                "Нехватка вернулась — приоритет источника тот же: " + again.tasks());
    }

    @Test
    void demand_TargetGrowsWithSpirits_RequestCoversWholeShortage() {
        GraphFacts oneFacts = facts(named(spirit("spirit-1")));
        GraphWorld one = stageTwo().with("food", 0);
        GraphTickets.Result single = GraphTickets.of(one.graph, one.state().withFacts(oneFacts), plan(one, oneFacts));
        int oneCount = single.post().stream()
                .filter(ticket -> "food".equals(ticket.task().makes()))
                .mapToInt(GraphTickets.Ticket::count)
                .findFirst()
                .orElse(0);

        GraphWorld two = stageTwo().with("food", 0);
        two.tools("spirit-2", 1);
        GraphTickets.Result pair = GraphTickets.of(two.graph, two.state().withFacts(facts(TWO)), plan(two, facts(TWO)));
        int twoCount = pair.post().stream()
                .filter(ticket -> "food".equals(ticket.task().makes()))
                .mapToInt(GraphTickets.Ticket::count)
                .findFirst()
                .orElse(0);

        assertEquals(16, oneCount, "Одному духу цель еды на этапе 2 — 16: " + single.post());
        assertEquals(32, twoCount, "Двум духам цель еды на этапе 2 — 32: " + pair.post());
        assertTrue(
                twoCount > oneCount,
                "Цель по типу растёт с числом духов, а не фиксирована: " + oneCount + " → " + twoCount);
    }
}
