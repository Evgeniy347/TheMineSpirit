package com.theminespirit.core.route;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.GoalBlock;
import com.theminespirit.core.observation.BlockCoord;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RouteBudgetTests {
    private static final RouteRules RULES = RouteRules.load();

    @Test
    void plan_UnreachableGoal_StopsAtBudgetInsteadOfFullNodeLimit() {
        GridWorld grid = GridWorld.flat(0);
        AtomicInteger reads = new AtomicInteger();
        RouteWorld counted = (x, y, z) -> {
            reads.incrementAndGet();
            return grid.cell(x, y, z);
        };
        AtomicInteger checks = new AtomicInteger();
        RouteBudget budget = () -> checks.incrementAndGet() > 50;

        Route route = RoutePlanner.plan(
                counted, new BlockCoord(0, 0, 0), new GoalBlock(new BlockCoord(200, 50, 0)), RULES, 0, budget);

        assertTrue(
                route.nodes() < 2000,
                "Недостижимая цель при исчерпанном бюджете обязана остановить A*: иначе один тик держит серверный "
                        + "поток секундами (observe15 A6). Узлов: " + route.nodes() + " при пределе "
                        + RULES.maxNodes());
        assertTrue(
                reads.get() < 40000,
                "Чтений мира при остановке по бюджету меньше полного предела узлов: фактически " + reads.get());
        assertTrue(
                checks.get() >= 50,
                "Бюджет проверяется на каждом шаге поиска: иначе предел работы не наступит. Проверок: " + checks.get());
    }
}
