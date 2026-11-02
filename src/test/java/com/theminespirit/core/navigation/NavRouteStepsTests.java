// lib: Из Baritone (github.com/cabaletta/baritone, pathing/calc/Path.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
