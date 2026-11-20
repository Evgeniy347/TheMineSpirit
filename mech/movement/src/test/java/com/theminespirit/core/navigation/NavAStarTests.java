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

        assertEquals(
                NavSearchStatus.FOUND,
                result.status(),
                "по ровному полу путь есть"
                        + " Иначе тест flatGround_TargetAhead_FindsStraightPath перестанет ловить эту поломку. При смене flatGround_TargetAhead_FindsStraightPath проверь это утверждение.");
        assertEquals(
                9,
                result.path().length(),
                "восемь шагов и старт"
                        + " Иначе тест flatGround_TargetAhead_FindsStraightPath перестанет ловить эту поломку. При смене flatGround_TargetAhead_FindsStraightPath проверь это утверждение.");
        assertEquals(
                NavActionCosts.WALK_ONE_BLOCK_COST * 8,
                result.path().cost(),
                1e-6,
                "цена — восемь шагов"
                        + " При смене flatGround_TargetAhead_FindsStraightPath проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне.");
        assertEquals(
                8,
                result.end().x(),
                "путь доходит до цели"
                        + " Иначе тест flatGround_TargetAhead_FindsStraightPath перестанет ловить эту поломку. При смене flatGround_TargetAhead_FindsStraightPath проверь это утверждение.");
    }

    @Test
    void wallWithGap_TargetBehind_GoesAround() {
        NavTestWorld world = NavTestWorld.flat(0);
        for (int z = -2; z <= 2; z++) {
            world.solid(4, 1, z).solid(4, 2, z);
        }
        NavResult result = plan(world, 64, settings(100_000, 10_000, true), new NavGoalBlock(8, 1, 0));

        assertEquals(
                NavSearchStatus.FOUND,
                result.status(),
                "стена с проходом обходится"
                        + " Иначе тест wallWithGap_TargetBehind_GoesAround перестанет ловить эту поломку. При смене wallWithGap_TargetBehind_GoesAround проверь это утверждение.");
        assertTrue(
                result.path().positions().stream().anyMatch(cell -> Math.abs(cell.z()) >= 3),
                "обход идёт мимо стены: " + result.path().positions()
                        + " Иначе тест wallWithGap_TargetBehind_GoesAround перестанет ловить эту поломку. При смене wallWithGap_TargetBehind_GoesAround проверь это утверждение.");
    }

    @Test
    void breakableWall_NoBlocks_BreaksThrough() {
        NavTestWorld world = NavTestWorld.flat(0);
        for (int z = -10; z <= 10; z++) {
            world.breakable(4, 1, z).breakable(4, 2, z);
        }
        NavResult result = plan(world, 0, settings(100_000, 10_000, true), new NavGoalBlock(8, 1, 0));

        assertEquals(
                NavSearchStatus.FOUND,
                result.status(),
                "ломаемая стена проходится"
                        + " Иначе тест breakableWall_NoBlocks_BreaksThrough перестанет ловить эту поломку. При смене breakableWall_NoBlocks_BreaksThrough проверь это утверждение.");
        assertTrue(
                result.path().positions().stream().anyMatch(cell -> cell.x() == 4),
                "без блоков дух проходит стену: " + result.path().positions()
                        + " Иначе тест breakableWall_NoBlocks_BreaksThrough перестанет ловить эту поломку. При смене breakableWall_NoBlocks_BreaksThrough проверь это утверждение.");
        assertTrue(
                result.path().cost() > NavActionCosts.WALK_ONE_BLOCK_COST * 8 + 10,
                "проход сквозь ломаемую стену оплачен ломкой: " + result.path().cost()
                        + " Иначе тест breakableWall_NoBlocks_BreaksThrough перестанет ловить эту поломку. При смене breakableWall_NoBlocks_BreaksThrough проверь это утверждение.");
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
                    "неразрушимая стена не проламывается: " + result.path().positions()
                            + " Иначе тест unbreakableWall_NoGap_GoesAroundOrFails перестанет ловить эту поломку. При смене unbreakableWall_NoGap_GoesAroundOrFails проверь это утверждение.");
        }
    }

    @Test
    void pit_OneDeep_DescendsAndClimbsOut() {
        NavTestWorld world = NavTestWorld.flat(0).air(4, 0, 0);
        NavSettings settings = settings(100_000, 10_000, true);
        NavContext context = new NavContext(world, settings, 64);
        NavResult down = NavAStarTestRun.plan(context, 0, 1, 0, new NavGoalBlock(4, 0, 0));
        NavResult up = NavAStarTestRun.plan(context, 4, 0, 0, new NavGoalBlock(8, 1, 0));

        assertEquals(
                NavSearchStatus.FOUND,
                down.status(),
                "яма глубиной 1 проходится вниз"
                        + " Иначе тест pit_OneDeep_DescendsAndClimbsOut перестанет ловить эту поломку. При смене pit_OneDeep_DescendsAndClimbsOut проверь это утверждение.");
        assertEquals(
                0,
                down.end().y(),
                "путь спускается в яму"
                        + " Иначе тест pit_OneDeep_DescendsAndClimbsOut перестанет ловить эту поломку. При смене pit_OneDeep_DescendsAndClimbsOut проверь это утверждение.");
        assertEquals(
                NavSearchStatus.FOUND,
                up.status(),
                "из ямы глубиной 1 есть выход"
                        + " Иначе тест pit_OneDeep_DescendsAndClimbsOut перестанет ловить эту поломку. При смене pit_OneDeep_DescendsAndClimbsOut проверь это утверждение.");
        assertEquals(
                1,
                up.end().y(),
                "путь поднимается из ямы"
                        + " Иначе тест pit_OneDeep_DescendsAndClimbsOut перестанет ловить эту поломку. При смене pit_OneDeep_DescendsAndClimbsOut проверь это утверждение.");
    }

    @Test
    void chunkBoundary_NextChunkUnloaded_StopsWithSegment() {
        NavTestWorld world = NavTestWorld.flat(0).unloadFrom(1);
        NavResult result = plan(world, 64, settings(100_000, 10_000, true), new NavGoalBlock(32, 1, 0));

        assertEquals(
                NavSearchStatus.SEGMENT,
                result.status(),
                "на границе незагруженного отдаётся сегмент"
                        + " Иначе тест chunkBoundary_NextChunkUnloaded_StopsWithSegment перестанет ловить эту поломку. При смене chunkBoundary_NextChunkUnloaded_StopsWithSegment проверь это утверждение.");
        assertTrue(
                result.end().x() < 16,
                "сегмент не заходит в незагруженный чанк: " + result.end()
                        + " Иначе тест chunkBoundary_NextChunkUnloaded_StopsWithSegment перестанет ловить эту поломку. При смене chunkBoundary_NextChunkUnloaded_StopsWithSegment проверь это утверждение.");
    }

    @Test
    void maxNodes_SmallLimit_StopsSearch() {
        NavSettings limited = new NavSettings(10_000, 10_000, 30, 50, 3, 12, 2, 20, 2, true, true);
        NavResult result = plan(NavTestWorld.flat(0), 64, limited, new NavGoalBlock(500, 1, 0));

        assertTrue(
                result.nodes() <= 30,
                "поиск уважает предел узлов: " + result.nodes()
                        + " Иначе тест maxNodes_SmallLimit_StopsSearch перестанет ловить эту поломку. При смене maxNodes_SmallLimit_StopsSearch проверь это утверждение.");
        assertFalse(
                result.found(),
                "за 30 узлов цель не достигается"
                        + " Иначе тест maxNodes_SmallLimit_StopsSearch перестанет ловить эту поломку. При смене maxNodes_SmallLimit_StopsSearch проверь это утверждение.");
    }

    @Test
    void timeout_ClockAdvances_StopsSearch() {
        AtomicLong now = new AtomicLong();
        LongSupplier clock = () -> now.addAndGet(1_000_000L);
        NavSettings timed = new NavSettings(5, 5, 100_000, 50, 3, 12, 2, 20, 2, true, true);
        NavResult result = NavAStarTestRun.plan(
                new NavContext(NavTestWorld.flat(0), timed, 64), 0, 1, 0, new NavGoalBlock(500, 1, 0), clock);

        assertFalse(
                result.found(),
                "по сроку цель не достигается"
                        + " Иначе тест timeout_ClockAdvances_StopsSearch перестанет ловить эту поломку. При смене timeout_ClockAdvances_StopsSearch проверь это утверждение.");
        assertTrue(
                result.nodes() < 400,
                "срок останавливает поиск задолго до предела узлов: " + result.nodes()
                        + " Иначе тест timeout_ClockAdvances_StopsSearch перестанет ловить эту поломку. При смене timeout_ClockAdvances_StopsSearch проверь это утверждение.");
    }

    @Test
    void goalAtStart_AlreadyThere_ReturnsSingleCell() {
        NavResult result = plan(NavTestWorld.flat(0), 64, settings(100_000, 10_000, true), new NavGoalBlock(0, 1, 0));

        assertEquals(
                NavSearchStatus.FOUND,
                result.status(),
                "цель уже достигнута"
                        + " Иначе тест goalAtStart_AlreadyThere_ReturnsSingleCell перестанет ловить эту поломку. При смене goalAtStart_AlreadyThere_ReturnsSingleCell проверь это утверждение.");
        assertEquals(
                1,
                result.path().length(),
                "путь из одной клетки"
                        + " Иначе тест goalAtStart_AlreadyThere_ReturnsSingleCell перестанет ловить эту поломку. При смене goalAtStart_AlreadyThere_ReturnsSingleCell проверь это утверждение.");
        assertEquals(
                0,
                result.path().cost(),
                0,
                "идти некуда — цена нулевая"
                        + " При смене goalAtStart_AlreadyThere_ReturnsSingleCell проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне.");
    }

    @Test
    void gap_OneBlockHole_JumpsAcross() {
        NavTestWorld world = NavTestWorld.flat(0).air(1, 0, 0);
        NavResult result = plan(world, 0, walkOnly(), new NavGoalBlock(2, 1, 0));

        assertEquals(
                NavSearchStatus.FOUND,
                result.status(),
                "щель в один блок перепрыгивается"
                        + " Иначе тест gap_OneBlockHole_JumpsAcross перестанет ловить эту поломку. При смене gap_OneBlockHole_JumpsAcross проверь это утверждение.");
        assertEquals(
                2,
                result.end().x(),
                "прыжок приводит на другой край"
                        + " Иначе тест gap_OneBlockHole_JumpsAcross перестанет ловить эту поломку. При смене gap_OneBlockHole_JumpsAcross проверь это утверждение.");
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

        assertFalse(
                result.found(),
                "из колодца без ступеней пути нет: навигация не копает и не ставит блоки"
                        + " Иначе тест well_NoStairs_NoPath перестанет ловить эту поломку. При смене well_NoStairs_NoPath проверь это утверждение.");
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
