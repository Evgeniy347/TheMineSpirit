package com.theminespirit.core.route;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.GoalNear;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteLiveSnapshotTests {
    private static final BlockCoord SPIRIT = new BlockCoord(-171, 66, -493);
    private static final BlockCoord HOME = new BlockCoord(-170, 68, -487);
    private static final BlockCoord ANCHOR = new BlockCoord(-155, 71, -489);
    private static final BlockCoord YARD_CHEST = new BlockCoord(-153, 72, -491);
    private static final double MAX_COST_TICKS = 600;
    private static final int MAX_NODES = 5_000;
    private static final SnapshotWorld WORLD = SnapshotWorld.load("/route/live-2610.txt");

    @Test
    void plan_LiveSpiritUnderGround_ReachesHome() {
        Route route = RoutePlanner.plan(WORLD, SPIRIT, new GoalNear(HOME, 2), RouteRules.load(), 0);

        RoutePlannerTests.assertFound(WORLD, SPIRIT, route, "живой снимок 26.10: из тоннеля к дому");
        assertBudget(route, "к дому");
    }

    @Test
    void plan_LiveSpiritUnderGround_ReachesAnchor() {
        Route route = RoutePlanner.plan(WORLD, SPIRIT, new GoalNear(ANCHOR, 1), RouteRules.load(), 0);

        RoutePlannerTests.assertFound(WORLD, SPIRIT, route, "живой снимок 26.10: из тоннеля к якорю");
        assertBudget(route, "к якорю");
    }

    @Test
    void plan_LiveSpiritWithBlocks_ReachesAnchorNotDearer() {
        Route dig = RoutePlanner.plan(WORLD, SPIRIT, new GoalNear(ANCHOR, 1), RouteRules.load(), 0);
        Route build = RoutePlanner.plan(WORLD, SPIRIT, new GoalNear(ANCHOR, 1), RouteRules.load(), 32);

        RoutePlannerTests.assertFound(WORLD, SPIRIT, build, "живой снимок 26.10 с блоками");
        assertTrue(
                build.cost() <= dig.cost() + 1e-9, "С блоками маршрут не дороже: " + build.cost() + " > " + dig.cost());
    }

    @Test
    void plan_LiveIncident85UnderGround_ReachesYardChest() {
        for (BlockCoord start : List.of(new BlockCoord(-166, 63, -496), new BlockCoord(-166, 62, -497))) {
            Route route = RoutePlanner.plan(WORLD, start, new GoalNear(YARD_CHEST, 2), RouteRules.load(), 0);

            RoutePlannerTests.assertFound(WORLD, start, route, "INC-85: из-под земли " + start + " к сундуку двора");
            assertBudget(route, "к сундуку двора из " + start);
        }
    }

    private static void assertBudget(Route route, String what) {
        assertTrue(
                route.cost() <= MAX_COST_TICKS && route.nodes() <= MAX_NODES,
                "Маршрут " + what + " на живом снимке: цена " + route.cost() + " тиков (предел " + MAX_COST_TICKS
                        + "), узлов " + route.nodes() + " (предел " + MAX_NODES + "), ломок "
                        + route.count(RouteStep.Action.BREAK) + ": рост — повод проверить цены ходов и эвристику");
    }
}
