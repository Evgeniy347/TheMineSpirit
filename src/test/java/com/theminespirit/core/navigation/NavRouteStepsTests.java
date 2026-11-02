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
    void toRoute_MissingFloor_PlacesBridge() {
        Route route = convert(NavTestWorld.flat(0).air(1, 0, 0), NavSearchStatus.FOUND, cell(0, 1, 0), cell(1, 1, 0));

        assertEquals(RouteStep.Action.PLACE, route.steps().get(0).action(), "над пропастью кладут блок");
        assertEquals(cell(1, 0, 0), route.steps().get(0).cell(), "блок кладут под ноги");
        assertEquals(RouteStep.Action.WALK, route.steps().get(1).action(), "после моста идут шагом");
    }

    @Test
    void toRoute_BreakableWall_BreaksBodyAndHead() {
        NavTestWorld world = NavTestWorld.flat(0).solid(1, 1, 0).solid(1, 2, 0);
        Route route = convert(world, NavSearchStatus.FOUND, cell(0, 1, 0), cell(1, 1, 0));

        assertEquals(3, route.steps().size(), "тело, голова и шаг");
        assertEquals(RouteStep.Action.BREAK, route.steps().get(0).action(), "первым ломают тело");
        assertEquals(RouteStep.Action.BREAK, route.steps().get(1).action(), "затем голову");
        assertEquals(RouteStep.Action.WALK, route.steps().get(2).action(), "после прохода идут шагом");
    }

    @Test
    void toRoute_VerticalStep_UsesPillar() {
        Route route = convert(NavTestWorld.flat(0), NavSearchStatus.FOUND, cell(0, 1, 0), cell(0, 2, 0));

        assertEquals(1, route.steps().size(), "подъём — один шаг");
        assertEquals(RouteStep.Action.PILLAR, route.steps().get(0).action(), "вверх на блок — столб");
        assertEquals(cell(0, 1, 0), route.steps().get(0).cell(), "столб ставится под текущие ноги");
    }

    @Test
    void toRoute_Water_Swims() {
        Route route = convert(NavTestWorld.flat(0).wet(1, 1, 0), NavSearchStatus.FOUND, cell(0, 1, 0), cell(1, 1, 0));

        assertEquals(RouteStep.Action.SWIM, route.steps().get(0).action(), "в воде плывут");
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
