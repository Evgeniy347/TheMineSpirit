// lib: Из Baritone (github.com/cabaletta/baritone, pathing/calc/Path.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.Route;
import com.theminespirit.core.route.RouteStep;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NavRouteStepsTests {
    @Test
    void toRoute_FlatPath_WalksEveryCell() {
        Route route = convert(NavTestWorld.flat(0), NavSearchStatus.FOUND, cell(0, 1, 0), cell(1, 1, 0), cell(2, 1, 0));

        assertEquals(Route.Status.FOUND, route.status(), "найденный путь остаётся найденным");
        assertEquals(2, route.steps().size(), "два перехода — два шага");
        assertEquals(RouteStep.Action.WALK, route.steps().get(0).action(), "по полу идут шагом");
        assertEquals(cell(2, 1, 0), route.end(), "конец пути сохраняется");
    }

    @Test
    void toRoute_Water_Swims() {
        Route route = convert(NavTestWorld.flat(0).wet(1, 1, 0), NavSearchStatus.FOUND, cell(0, 1, 0), cell(1, 1, 0));

        assertEquals(RouteStep.Action.SWIM, route.steps().get(0).action(), "в воде плывут");
    }

    @Test
    void toRoute_VerticalStep_RemainsWalk() {
        Route route = convert(NavTestWorld.flat(0), NavSearchStatus.FOUND, cell(0, 1, 0), cell(0, 2, 0));

        assertEquals(1, route.steps().size(), "подъём — один шаг");
        assertEquals(
                RouteStep.Action.WALK, route.steps().get(0).action(), "навигация не ставит блоки: подъём идёт ходьбой");
    }

    @Test
    void toRoute_AStarBreakableWall_ConvertsToBreakSteps() {
        NavTestWorld world = NavTestWorld.flat(0);
        for (int z = -10; z <= 10; z++) {
            world.breakable(4, 1, z).breakable(4, 2, z);
        }
        NavSettings settings = new NavSettings(10_000, 10_000, 200_000, 50, 3, 12, 2, 20, 2, true, false);
        NavResult planned =
                NavAStarTestRun.plan(new NavContext(world, settings, 0), 0, 1, 0, new NavGoalBlock(8, 1, 0));
        assertEquals(NavSearchStatus.FOUND, planned.status(), "A* обязан найти путь сквозь ломаемую стену");

        NavSegmentedRoute segmented = new NavSegmentedRoute(
                planned.status(), planned.path().positions(), 1, planned.path().length(), 0, planned.end());
        Route route = NavRouteSteps.toRoute(segmented, world, planned.end());

        assertEquals(Route.Status.FOUND, route.status(), "путь A* сквозь свою стену обязан дойти до маршрута");
        assertTrue(
                route.steps().stream()
                        .anyMatch(step -> step.action() == RouteStep.Action.BREAK
                                && step.cell().x() == 4),
                "маршрут обязан содержать ломку своей стены в x=4: " + route.steps());
        assertTrue(
                route.steps().stream().anyMatch(step -> step.cell().x() == 8),
                "после ломки маршрут доходит до цели: " + route.steps());
    }

    @Test
    void toRoute_BreakableCell_BreaksThenWalks() {
        NavTestWorld world = NavTestWorld.flat(0).breakable(1, 1, 0).breakable(1, 2, 0);
        Route route = convert(world, NavSearchStatus.FOUND, cell(0, 1, 0), cell(1, 1, 0));

        assertEquals(Route.Status.FOUND, route.status(), "ломаемая преграда оставляет найденный маршрут");
        assertEquals(3, route.steps().size(), "две разбираемые клетки и шаг в освободившуюся: " + route.steps());
        assertEquals(
                RouteStep.Action.BREAK, route.steps().get(0).action(), "сначала ломают клетку ног, а не идут сквозь");
        assertEquals(cell(1, 1, 0), route.steps().get(0).cell(), "ломают именно преграду на пути");
        assertEquals(
                RouteStep.Action.BREAK, route.steps().get(1).action(), "клетку головы тоже освобождают перед шагом");
        assertEquals(cell(1, 2, 0), route.steps().get(1).cell(), "вторая ломка — над преградой");
        assertEquals(RouteStep.Action.WALK, route.steps().get(2).action(), "после расчистки входят в клетку шагом");
    }

    @Test
    void toRoute_BreakableHeadOnly_BreaksHeadThenWalks() {
        NavTestWorld world = NavTestWorld.flat(0).breakable(1, 2, 0);
        Route route = convert(world, NavSearchStatus.FOUND, cell(0, 1, 0), cell(1, 1, 0));

        assertEquals(2, route.steps().size(), "преграда только на голове даёт одну ломку и шаг: " + route.steps());
        assertEquals(RouteStep.Action.BREAK, route.steps().get(0).action(), "голову освобождают до входа");
        assertEquals(cell(1, 2, 0), route.steps().get(0).cell(), "ломают клетку над местом шага");
        assertEquals(RouteStep.Action.WALK, route.steps().get(1).action(), "после ломки идут вперёд");
    }

    @Test
    void toRoute_BlockedCell_ReturnsNoneInsteadOfBreak() {
        NavTestWorld world = NavTestWorld.flat(0).solid(1, 1, 0).solid(1, 2, 0);
        Route route = convert(world, NavSearchStatus.FOUND, cell(0, 1, 0), cell(1, 1, 0));

        assertEquals(Route.Status.NONE, route.status(), "непроходимая клетка — не повод ломать: пути нет");
        assertEquals(0, route.steps().size(), "шагов нет: навигация не меняет мир");
    }

    @Test
    void toRoute_Segment_IsPartial() {
        Route route = convert(NavTestWorld.flat(0), NavSearchStatus.SEGMENT, cell(0, 1, 0), cell(1, 1, 0));

        assertEquals(Route.Status.PARTIAL, route.status(), "сегмент — это частичный маршрут");
    }

    private static Route convert(NavTestWorld world, NavSearchStatus status, BlockCoord... positions) {
        List<BlockCoord> cells = new ArrayList<>(List.of(positions));
        NavSegmentedRoute segmented = new NavSegmentedRoute(status, cells, 1, 1, 0, cells.get(cells.size() - 1));
        return NavRouteSteps.toRoute(segmented, world, cells.get(cells.size() - 1));
    }

    private static BlockCoord cell(int x, int y, int z) {
        return new BlockCoord(x, y, z);
    }
}
