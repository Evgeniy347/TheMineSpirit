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

        assertEquals(
                Route.Status.FOUND,
                route.status(),
                "найденный путь остаётся найденным"
                        + " Иначе тест toRoute_FlatPath_WalksEveryCell перестанет ловить эту поломку. При смене toRoute_FlatPath_WalksEveryCell проверь это утверждение.");
        assertEquals(
                2,
                route.steps().size(),
                "два перехода — два шага"
                        + " Иначе тест toRoute_FlatPath_WalksEveryCell перестанет ловить эту поломку. При смене toRoute_FlatPath_WalksEveryCell проверь это утверждение.");
        assertEquals(
                RouteStep.Action.WALK,
                route.steps().get(0).action(),
                "по полу идут шагом"
                        + " Иначе тест toRoute_FlatPath_WalksEveryCell перестанет ловить эту поломку. При смене toRoute_FlatPath_WalksEveryCell проверь это утверждение.");
        assertEquals(
                cell(2, 1, 0),
                route.end(),
                "конец пути сохраняется"
                        + " Иначе тест toRoute_FlatPath_WalksEveryCell перестанет ловить эту поломку. При смене toRoute_FlatPath_WalksEveryCell проверь это утверждение.");
    }

    @Test
    void toRoute_AStarGap_PreservesJumpInEveryDirection() {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            BlockCoord from = cell(-164, 75, -487);
            BlockCoord target = cell(from.x() + 2 * direction[0], from.y(), from.z() + 2 * direction[1]);
            NavTestWorld world = NavTestWorld.flat(69)
                    .solid(from.x(), from.y() - 1, from.z())
                    .solid(target.x(), target.y() - 1, target.z());
            NavSettings settings = new NavSettings(2000, 2000, 200_000, 50, 3, 12, 2, 20, 2, true, false);
            NavSegmentedSession session = new NavSegmentedSession(
                    new NavContext(world, settings, 0),
                    from,
                    new NavGoalBlock(target.x(), target.y(), target.z()),
                    32,
                    6,
                    0.5);
            NavSegmentedRoute planned = session.step(Long.MAX_VALUE);
            assertEquals(
                    NavSearchStatus.FOUND,
                    planned.status(),
                    "Щель над ямой глубиной пять блоков пересекается прыжком без опор"
                            + " Иначе тест toRoute_AStarGap_PreservesJumpInEveryDirection перестанет ловить эту поломку. При смене toRoute_AStarGap_PreservesJumpInEveryDirection проверь это утверждение.");
            Route route = NavRouteSteps.toRoute(planned, world, target);
            assertEquals(
                    1,
                    route.steps().size(),
                    "Две соседние опоры разделены одной щелью: промежуточной стоянки нет"
                            + " Иначе тест toRoute_AStarGap_PreservesJumpInEveryDirection перестанет ловить эту поломку. При смене toRoute_AStarGap_PreservesJumpInEveryDirection проверь это утверждение.");
            assertEquals(
                    "JUMP",
                    route.steps().get(0).action().name(),
                    "Прыжок A* не превращается в ходьбу: иначе дух падает в яму, как на живом сервере 17.11"
                            + " При смене toRoute_AStarGap_PreservesJumpInEveryDirection проверь это утверждение.");
            assertEquals(
                    target,
                    route.steps().get(0).cell(),
                    "Прыжок адресован противоположной опоре во всех четырёх направлениях"
                            + " Иначе тест toRoute_AStarGap_PreservesJumpInEveryDirection перестанет ловить эту поломку. При смене toRoute_AStarGap_PreservesJumpInEveryDirection проверь это утверждение.");
        }
    }

    @Test
    void toRoute_Water_Swims() {
        Route route = convert(NavTestWorld.flat(0).wet(1, 1, 0), NavSearchStatus.FOUND, cell(0, 1, 0), cell(1, 1, 0));

        assertEquals(
                RouteStep.Action.SWIM,
                route.steps().get(0).action(),
                "в воде плывут"
                        + " Иначе тест toRoute_Water_Swims перестанет ловить эту поломку. При смене toRoute_Water_Swims проверь это утверждение.");
    }

    @Test
    void toRoute_VerticalStep_RemainsWalk() {
        Route route = convert(NavTestWorld.flat(0), NavSearchStatus.FOUND, cell(0, 1, 0), cell(0, 2, 0));

        assertEquals(
                1,
                route.steps().size(),
                "подъём — один шаг"
                        + " Иначе тест toRoute_VerticalStep_RemainsWalk перестанет ловить эту поломку. При смене toRoute_VerticalStep_RemainsWalk проверь это утверждение.");
        assertEquals(
                RouteStep.Action.WALK,
                route.steps().get(0).action(),
                "навигация не ставит блоки: подъём идёт ходьбой"
                        + " Иначе тест toRoute_VerticalStep_RemainsWalk перестанет ловить эту поломку. При смене toRoute_VerticalStep_RemainsWalk проверь это утверждение.");
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
        assertEquals(
                NavSearchStatus.FOUND,
                planned.status(),
                "A* обязан найти путь сквозь ломаемую стену"
                        + " Иначе тест toRoute_AStarBreakableWall_ConvertsToBreakSteps перестанет ловить эту поломку. При смене toRoute_AStarBreakableWall_ConvertsToBreakSteps проверь это утверждение.");

        NavSegmentedRoute segmented = new NavSegmentedRoute(
                planned.status(), planned.path().positions(), 1, planned.path().length(), 0, planned.end());
        Route route = NavRouteSteps.toRoute(segmented, world, planned.end());

        assertEquals(
                Route.Status.FOUND,
                route.status(),
                "путь A* сквозь свою стену обязан дойти до маршрута"
                        + " Иначе тест toRoute_AStarBreakableWall_ConvertsToBreakSteps перестанет ловить эту поломку. При смене toRoute_AStarBreakableWall_ConvertsToBreakSteps проверь это утверждение.");
        assertTrue(
                route.steps().stream()
                        .anyMatch(step -> step.action() == RouteStep.Action.BREAK
                                && step.cell().x() == 4),
                "маршрут обязан содержать ломку своей стены в x=4: " + route.steps()
                        + " Иначе тест toRoute_AStarBreakableWall_ConvertsToBreakSteps перестанет ловить эту поломку. При смене toRoute_AStarBreakableWall_ConvertsToBreakSteps проверь это утверждение.");
        assertTrue(
                route.steps().stream().anyMatch(step -> step.cell().x() == 8),
                "после ломки маршрут доходит до цели: " + route.steps()
                        + " Иначе тест toRoute_AStarBreakableWall_ConvertsToBreakSteps перестанет ловить эту поломку. При смене toRoute_AStarBreakableWall_ConvertsToBreakSteps проверь это утверждение.");
    }

    @Test
    void toRoute_SolidHeadNotBreakable_StillWalks() {
        NavTestWorld world = NavTestWorld.flat(0).solid(1, 2, 0);
        Route route = convert(world, NavSearchStatus.FOUND, cell(0, 1, 0), cell(1, 1, 0));

        assertEquals(
                Route.Status.FOUND,
                route.status(),
                "Твёрдый потолок над проходимой клеткой не отменяет маршрут"
                        + " Иначе тест toRoute_SolidHeadNotBreakable_StillWalks перестанет ловить эту поломку. При смене toRoute_SolidHeadNotBreakable_StillWalks проверь это утверждение.");
        assertEquals(
                1,
                route.steps().size(),
                "Один шаг без лишних ломок: " + route.steps()
                        + " Иначе тест toRoute_SolidHeadNotBreakable_StillWalks перестанет ловить эту поломку. При смене toRoute_SolidHeadNotBreakable_StillWalks проверь это утверждение.");
        assertEquals(
                RouteStep.Action.WALK,
                route.steps().get(0).action(),
                "Клетку головы ломают только если она ломаемая: иначе узкий проход dig_out снова упирается в тупик"
                        + " При смене toRoute_SolidHeadNotBreakable_StillWalks проверь это утверждение.");
    }

    @Test
    void toRoute_BreakableCell_BreaksThenWalks() {
        NavTestWorld world = NavTestWorld.flat(0).breakable(1, 1, 0).breakable(1, 2, 0);
        Route route = convert(world, NavSearchStatus.FOUND, cell(0, 1, 0), cell(1, 1, 0));

        assertEquals(
                Route.Status.FOUND,
                route.status(),
                "ломаемая преграда оставляет найденный маршрут"
                        + " Иначе тест toRoute_BreakableCell_BreaksThenWalks перестанет ловить эту поломку. При смене toRoute_BreakableCell_BreaksThenWalks проверь это утверждение.");
        assertEquals(
                3,
                route.steps().size(),
                "две разбираемые клетки и шаг в освободившуюся: " + route.steps()
                        + " Иначе тест toRoute_BreakableCell_BreaksThenWalks перестанет ловить эту поломку. При смене toRoute_BreakableCell_BreaksThenWalks проверь это утверждение.");
        assertEquals(
                RouteStep.Action.BREAK,
                route.steps().get(0).action(),
                "сначала ломают клетку ног, а не идут сквозь"
                        + " Иначе тест toRoute_BreakableCell_BreaksThenWalks перестанет ловить эту поломку. При смене toRoute_BreakableCell_BreaksThenWalks проверь это утверждение.");
        assertEquals(
                cell(1, 1, 0),
                route.steps().get(0).cell(),
                "ломают именно преграду на пути"
                        + " Иначе тест toRoute_BreakableCell_BreaksThenWalks перестанет ловить эту поломку. При смене toRoute_BreakableCell_BreaksThenWalks проверь это утверждение.");
        assertEquals(
                RouteStep.Action.BREAK,
                route.steps().get(1).action(),
                "клетку головы тоже освобождают перед шагом"
                        + " Иначе тест toRoute_BreakableCell_BreaksThenWalks перестанет ловить эту поломку. При смене toRoute_BreakableCell_BreaksThenWalks проверь это утверждение.");
        assertEquals(
                cell(1, 2, 0),
                route.steps().get(1).cell(),
                "вторая ломка — над преградой"
                        + " Иначе тест toRoute_BreakableCell_BreaksThenWalks перестанет ловить эту поломку. При смене toRoute_BreakableCell_BreaksThenWalks проверь это утверждение.");
        assertEquals(
                RouteStep.Action.WALK,
                route.steps().get(2).action(),
                "после расчистки входят в клетку шагом"
                        + " Иначе тест toRoute_BreakableCell_BreaksThenWalks перестанет ловить эту поломку. При смене toRoute_BreakableCell_BreaksThenWalks проверь это утверждение.");
    }

    @Test
    void toRoute_BreakableHeadOnly_BreaksHeadThenWalks() {
        NavTestWorld world = NavTestWorld.flat(0).breakable(1, 2, 0);
        Route route = convert(world, NavSearchStatus.FOUND, cell(0, 1, 0), cell(1, 1, 0));

        assertEquals(
                2,
                route.steps().size(),
                "преграда только на голове даёт одну ломку и шаг: " + route.steps()
                        + " Иначе тест toRoute_BreakableHeadOnly_BreaksHeadThenWalks перестанет ловить эту поломку. При смене toRoute_BreakableHeadOnly_BreaksHeadThenWalks проверь это утверждение.");
        assertEquals(
                RouteStep.Action.BREAK,
                route.steps().get(0).action(),
                "голову освобождают до входа"
                        + " Иначе тест toRoute_BreakableHeadOnly_BreaksHeadThenWalks перестанет ловить эту поломку. При смене toRoute_BreakableHeadOnly_BreaksHeadThenWalks проверь это утверждение.");
        assertEquals(
                cell(1, 2, 0),
                route.steps().get(0).cell(),
                "ломают клетку над местом шага"
                        + " Иначе тест toRoute_BreakableHeadOnly_BreaksHeadThenWalks перестанет ловить эту поломку. При смене toRoute_BreakableHeadOnly_BreaksHeadThenWalks проверь это утверждение.");
        assertEquals(
                RouteStep.Action.WALK,
                route.steps().get(1).action(),
                "после ломки идут вперёд"
                        + " Иначе тест toRoute_BreakableHeadOnly_BreaksHeadThenWalks перестанет ловить эту поломку. При смене toRoute_BreakableHeadOnly_BreaksHeadThenWalks проверь это утверждение.");
    }

    @Test
    void toRoute_BlockedCell_ReturnsNoneInsteadOfBreak() {
        NavTestWorld world = NavTestWorld.flat(0).solid(1, 1, 0).solid(1, 2, 0);
        Route route = convert(world, NavSearchStatus.FOUND, cell(0, 1, 0), cell(1, 1, 0));

        assertEquals(
                Route.Status.NONE,
                route.status(),
                "непроходимая клетка — не повод ломать: пути нет"
                        + " Иначе тест toRoute_BlockedCell_ReturnsNoneInsteadOfBreak перестанет ловить эту поломку. При смене toRoute_BlockedCell_ReturnsNoneInsteadOfBreak проверь это утверждение.");
        assertEquals(
                0,
                route.steps().size(),
                "шагов нет: навигация не меняет мир"
                        + " Иначе тест toRoute_BlockedCell_ReturnsNoneInsteadOfBreak перестанет ловить эту поломку. При смене toRoute_BlockedCell_ReturnsNoneInsteadOfBreak проверь это утверждение.");
    }

    @Test
    void toRoute_Segment_IsPartial() {
        Route route = convert(NavTestWorld.flat(0), NavSearchStatus.SEGMENT, cell(0, 1, 0), cell(1, 1, 0));

        assertEquals(
                Route.Status.PARTIAL,
                route.status(),
                "сегмент — это частичный маршрут"
                        + " Иначе тест toRoute_Segment_IsPartial перестанет ловить эту поломку. При смене toRoute_Segment_IsPartial проверь это утверждение.");
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
