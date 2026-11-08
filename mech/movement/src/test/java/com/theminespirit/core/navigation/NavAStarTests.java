// lib: Из Baritone (github.com/cabaletta/baritone, pathing/calc/AStarPathFinder.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class NavAStarTests {
    @Test
    void flatGround_TargetAhead_FindsStraightPath() {
        NavResult result = plan(NavTestWorld.flat(0), 64, settings(100_000, 10_000, true), new NavGoalBlock(8, 1, 0));

        assertEquals(NavSearchStatus.FOUND, result.status(), "по ровному полу путь есть");
        assertEquals(9, result.path().length(), "восемь шагов и старт");
        assertEquals(NavActionCosts.WALK_ONE_BLOCK_COST * 8, result.path().cost(), 1e-6, "цена — восемь шагов");
        assertEquals(8, result.end().x(), "путь доходит до цели");
    }

    @Test
    void wallWithGap_TargetBehind_GoesAround() {
        NavTestWorld world = NavTestWorld.flat(0);
        for (int z = -2; z <= 2; z++) {
            world.solid(4, 1, z).solid(4, 2, z);
        }
        NavResult result = plan(world, 64, settings(100_000, 10_000, true), new NavGoalBlock(8, 1, 0));

        assertEquals(NavSearchStatus.FOUND, result.status(), "стена с проходом обходится");
        assertTrue(
                result.path().positions().stream().anyMatch(cell -> Math.abs(cell.z()) >= 3),
                "обход идёт мимо стены: " + result.path().positions());
    }

    @Test
    void breakableWall_NoBlocks_BreaksThrough() {
        NavTestWorld world = NavTestWorld.flat(0);
        for (int z = -10; z <= 10; z++) {
            world.breakable(4, 1, z).breakable(4, 2, z);
        }
        NavResult result = plan(world, 0, settings(100_000, 10_000, true), new NavGoalBlock(8, 1, 0));

        assertEquals(NavSearchStatus.FOUND, result.status(), "ломаемая стена проходится");
        assertTrue(
                result.path().positions().stream().anyMatch(cell -> cell.x() == 4),
                "без блоков дух проходит стену: " + result.path().positions());
        assertTrue(
                result.path().cost() > NavActionCosts.WALK_ONE_BLOCK_COST * 8 + 10,
                "проход сквозь ломаемую стену оплачен ломкой: " + result.path().cost());
    }

    @Test
    void unbreakableWall_NoGap_GoesAroundOrFails() {
        NavTestWorld world = NavTestWorld.flat(0);
        for (int z = -10; z <= 10; z++) {
            world.solid(4, 1, z).solid(4, 2, z);
        }
        NavSettings noBreak = settings(100_000, 10_000, false);
        NavResult result = plan(world, 64, noBreak, new NavGoalBlock(8, 1, 0));

        if (result.found()) {
            assertTrue(
                    result.path().positions().stream().noneMatch(cell -> cell.x() == 4 && cell.y() <= 2),
                    "неразрушимая стена не проламывается: " + result.path().positions());
        }
    }

    @Test
    void pit_OneDeep_DescendsAndClimbsOut() {
        NavTestWorld world = NavTestWorld.flat(0).air(4, 0, 0);
        NavSettings settings = settings(100_000, 10_000, true);
        NavContext context = new NavContext(world, settings, 64);
        NavResult down = NavAStarTestRun.plan(context, 0, 1, 0, new NavGoalBlock(4, 0, 0));
        NavResult up = NavAStarTestRun.plan(context, 4, 0, 0, new NavGoalBlock(8, 1, 0));

        assertEquals(NavSearchStatus.FOUND, down.status(), "яма глубиной 1 проходится вниз");
        assertEquals(0, down.end().y(), "путь спускается в яму");
        assertEquals(NavSearchStatus.FOUND, up.status(), "из ямы глубиной 1 есть выход");
        assertEquals(1, up.end().y(), "путь поднимается из ямы");
    }

    @Test
    void chunkBoundary_NextChunkUnloaded_StopsWithSegment() {
        NavTestWorld world = NavTestWorld.flat(0).unloadFrom(1);
        NavResult result = plan(world, 64, settings(100_000, 10_000, true), new NavGoalBlock(32, 1, 0));

        assertEquals(NavSearchStatus.SEGMENT, result.status(), "на границе незагруженного отдаётся сегмент");
        assertTrue(result.end().x() < 16, "сегмент не заходит в незагруженный чанк: " + result.end());
    }

    @Test
    void maxNodes_SmallLimit_StopsSearch() {
        NavSettings limited = new NavSettings(10_000, 10_000, 30, 50, 3, 12, 2, 20, 2, true, true);
        NavResult result = plan(NavTestWorld.flat(0), 64, limited, new NavGoalBlock(500, 1, 0));

        assertTrue(result.nodes() <= 30, "поиск уважает предел узлов: " + result.nodes());
        assertFalse(result.found(), "за 30 узлов цель не достигается");
    }

    @Test
    void timeout_ClockAdvances_StopsSearch() {
        AtomicLong now = new AtomicLong();
        LongSupplier clock = () -> now.addAndGet(1_000_000L);
        NavSettings timed = new NavSettings(5, 5, 100_000, 50, 3, 12, 2, 20, 2, true, true);
        NavResult result = NavAStarTestRun.plan(
                new NavContext(NavTestWorld.flat(0), timed, 64), 0, 1, 0, new NavGoalBlock(500, 1, 0), clock);

        assertFalse(result.found(), "по сроку цель не достигается");
        assertTrue(result.nodes() < 400, "срок останавливает поиск задолго до предела узлов: " + result.nodes());
    }

    @Test
    void goalAtStart_AlreadyThere_ReturnsSingleCell() {
        NavResult result = plan(NavTestWorld.flat(0), 64, settings(100_000, 10_000, true), new NavGoalBlock(0, 1, 0));

        assertEquals(NavSearchStatus.FOUND, result.status(), "цель уже достигнута");
        assertEquals(1, result.path().length(), "путь из одной клетки");
        assertEquals(0, result.path().cost(), 0, "идти некуда — цена нулевая");
    }

    @Test
    void gap_OneBlockHole_JumpsAcross() {
        NavTestWorld world = NavTestWorld.flat(0).air(1, 0, 0);
        NavResult result = plan(world, 0, walkOnly(), new NavGoalBlock(2, 1, 0));

        assertEquals(NavSearchStatus.FOUND, result.status(), "щель в один блок перепрыгивается");
        assertEquals(2, result.end().x(), "прыжок приводит на другой край");
    }

    @Test
    void well_NoStairs_NoPath() {
        NavTestWorld world = NavTestWorld.flat(0)
                .air(0, -2, 0)
                .air(0, -1, 0)
                .air(0, 0, 0)
                .air(0, 1, 0)
                .air(0, 2, 0);
        NavResult result = planAt(world, new BlockCoord(0, -2, 0), walkOnly(), new NavGoalBlock(0, 1, 0));

        assertFalse(result.found(), "из колодца без ступеней пути нет: навигация не копает и не ставит блоки");
    }

    @Test
    void stairs_ThreeSteps_ClimbsOut() {
        NavTestWorld world = NavTestWorld.flat(0)
                .air(0, -2, 0)
                .air(0, -1, 0)
                .air(0, 0, 0)
                .air(0, 1, 0)
                .solid(1, -2, 0)
                .air(1, -1, 0)
                .air(1, 0, 0)
                .air(1, 1, 0)
                .solid(2, -1, 0)
                .air(2, 0, 0)
                .air(2, 1, 0)
                .solid(3, 0, 0)
                .air(3, 1, 0)
                .air(3, 2, 0);
        NavResult result = planAt(world, new BlockCoord(0, -2, 0), walkOnly(), new NavGoalBlock(3, 1, 0));

        assertEquals(NavSearchStatus.FOUND, result.status(), "по трём ступеням дух выходит из ямы");
        assertEquals(1, result.end().y(), "конец на уровне земли");
    }

    @Test
    void water_ShallowRiver_IsSwumAcross() {
        NavTestWorld world =
                NavTestWorld.flat(0).wet(1, 1, 0).wet(1, 2, 0).wet(2, 1, 0).wet(2, 2, 0);
        NavResult result = plan(world, 0, walkOnly(), new NavGoalBlock(3, 1, 0));

        assertEquals(NavSearchStatus.FOUND, result.status(), "неглубокая река переплывается");
        assertEquals(3, result.end().x(), "дух выходит на другом берегу");
    }

    @Test
    void pit_DeadEndUnderWall_PathDoesNotDescend() {
        NavTestWorld world = underWallCorridor(false);
        NavResult result = plan(world, 0, walkOnly(), new NavGoalBlock(10, 1, 0));

        assertFalse(result.found(), "яма без выхода не ведёт к цели: мост из неё не строится");
        assertTrue(
                result.path() == null || result.path().positions().stream().allMatch(cell -> cell.y() >= 1),
                "путь не заходит в яму под стеной: ниже поверхности нет ни одной клетки — "
                        + "иначе дух сходит с ума в ловушке (observe24 B1): "
                        + (result.path() == null ? "путь пуст" : result.path().positions()));
    }

    @Test
    void cave_UnderSettlementWithWater_PathStaysAboveGround() {
        NavTestWorld world = underWallCorridor(true);
        NavResult result = plan(world, 0, walkOnly(), new NavGoalBlock(10, 1, 0));

        assertTrue(
                result.path() == null || result.path().positions().stream().allMatch(cell -> cell.y() >= 1),
                "под поселением пещера с водой без выхода: путь не спускается туда, где вода держит духа — "
                        + (result.path() == null ? "путь пуст" : result.path().positions()));
    }

    private static NavTestWorld underWallCorridor(boolean water) {
        NavTestWorld world = NavTestWorld.flat(0);
        for (int x = 0; x <= 12; x++) {
            world.solid(x, 1, -1).solid(x, 2, -1).solid(x, 3, -1);
            world.solid(x, 1, 1).solid(x, 2, 1).solid(x, 3, 1);
        }
        for (int x = 4; x <= 9; x++) {
            world.solid(x, 1, 0).solid(x, 2, 0).solid(x, 3, 0);
        }
        world.solid(12, 1, 0).solid(12, 2, 0).solid(12, 3, 0);
        for (int x = 3; x <= 10; x++) {
            for (int z = -1; z <= 1; z++) {
                world.air(x, -2, z).air(x, -1, z);
            }
        }
        if (water) {
            for (int x = 7; x <= 10; x++) {
                world.wet(x, -2, 0);
            }
        }
        world.air(3, 0, 0);
        return world;
    }

    private static NavResult plan(NavTestWorld world, int blocks, NavSettings settings, NavGoal goal) {
        return NavAStarTestRun.plan(new NavContext(world, settings, blocks), 0, 1, 0, goal);
    }

    private static NavSettings settings(int maxNodes, long timeoutMs, boolean canBreak) {
        return new NavSettings(timeoutMs, timeoutMs, maxNodes, 50, 3, 12, 2, 20, 2, canBreak, true);
    }

    private static NavSettings walkOnly() {
        return new NavSettings(10_000, 10_000, 200_000, 50, 3, 12, 2, 20, 2, false, false);
    }

    private static NavResult planAt(NavTestWorld world, BlockCoord start, NavSettings settings, NavGoal goal) {
        return NavAStarTestRun.plan(new NavContext(world, settings, 0), start.x(), start.y(), start.z(), goal);
    }
}
