// lib: Из Baritone (github.com/cabaletta/baritone, pathing/calc/AStarPathFinder.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class NavAStarTestsPart2 {
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

    private static NavSettings walkOnly() {
        return new NavSettings(10_000, 10_000, 200_000, 50, 3, 12, 2, 20, 2, false, false);
    }

    private static NavResult planAt(NavTestWorld world, BlockCoord start, NavSettings settings, NavGoal goal) {
        return NavAStarTestRun.plan(new NavContext(world, settings, 0), start.x(), start.y(), start.z(), goal);
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

        assertEquals(
                NavSearchStatus.FOUND,
                result.status(),
                "по трём ступеням дух выходит из ямы"
                        + " Иначе тест stairs_ThreeSteps_ClimbsOut перестанет ловить эту поломку. При смене stairs_ThreeSteps_ClimbsOut проверь это утверждение.");
        assertEquals(
                1,
                result.end().y(),
                "конец на уровне земли"
                        + " Иначе тест stairs_ThreeSteps_ClimbsOut перестанет ловить эту поломку. При смене stairs_ThreeSteps_ClimbsOut проверь это утверждение.");
    }

    @Test
    void water_ShallowRiver_IsSwumAcross() {
        NavTestWorld world =
                NavTestWorld.flat(0).wet(1, 1, 0).wet(1, 2, 0).wet(2, 1, 0).wet(2, 2, 0);
        NavResult result = plan(world, 0, walkOnly(), new NavGoalBlock(3, 1, 0));

        assertEquals(
                NavSearchStatus.FOUND,
                result.status(),
                "неглубокая река переплывается"
                        + " Иначе тест water_ShallowRiver_IsSwumAcross перестанет ловить эту поломку. При смене water_ShallowRiver_IsSwumAcross проверь это утверждение.");
        assertEquals(
                3,
                result.end().x(),
                "дух выходит на другом берегу"
                        + " Иначе тест water_ShallowRiver_IsSwumAcross перестанет ловить эту поломку. При смене water_ShallowRiver_IsSwumAcross проверь это утверждение.");
    }

    @Test
    void pit_DeadEndUnderWall_PathDoesNotDescend() {
        NavTestWorld world = underWallCorridor(false);
        NavResult result = plan(world, 0, walkOnly(), new NavGoalBlock(10, 1, 0));

        assertFalse(
                result.found(),
                "яма без выхода не ведёт к цели: мост из неё не строится"
                        + " Иначе тест pit_DeadEndUnderWall_PathDoesNotDescend перестанет ловить эту поломку. При смене pit_DeadEndUnderWall_PathDoesNotDescend проверь это утверждение.");
        assertTrue(
                result.path() == null || result.path().positions().stream().allMatch(cell -> cell.y() >= 1),
                "путь не заходит в яму под стеной: ниже поверхности нет ни одной клетки — "
                        + "иначе дух сходит с ума в ловушке (observe24 B1): "
                        + (result.path() == null ? "путь пуст" : result.path().positions())
                        + " При смене pit_DeadEndUnderWall_PathDoesNotDescend проверь это утверждение.");
    }

    @Test
    void pillars_TwoHeightsOverFlatGround_BothDescend() {
        NavTestWorld world = NavTestWorld.flat(0);
        for (int y = 1; y <= 4; y++) {
            world.solid(0, y, 0);
        }
        for (int y = 1; y <= 5; y++) {
            world.solid(20, y, 20);
        }
        NavResult low = planAt(world, new BlockCoord(0, 5, 0), walkOnly(), new NavGoalBlock(8, 1, 0));
        NavResult high = planAt(world, new BlockCoord(20, 6, 20), walkOnly(), new NavGoalBlock(28, 1, 20));

        assertEquals(
                NavSearchStatus.FOUND,
                low.status(),
                "дух на одиночном столбе высотой 4 обязан спуститься на землю и дойти до цели: в observe25 "
                        + "spirit-1 стоял на блоке (-155,72,-490), а поиск отдавал NONE после 1–3 узлов, "
                        + "потому что падение глубже 3 запрещено, а обойти столб нечем"
                        + " При смене pillars_TwoHeightsOverFlatGround_BothDescend проверь это утверждение.");
        assertTrue(
                low.end().y() <= 1,
                "путь обязан спустить духа с опоры, а кончается в " + low.end()
                        + " Иначе тест pillars_TwoHeightsOverFlatGround_BothDescend перестанет ловить эту поломку. При смене pillars_TwoHeightsOverFlatGround_BothDescend проверь это утверждение.");
        assertEquals(
                NavSearchStatus.FOUND,
                high.status(),
                "столб высотой 5 (spirit-2 observe25) — тот же спуск: одиночную опору нельзя запирать "
                        + "пределом падения 3"
                        + " Иначе тест pillars_TwoHeightsOverFlatGround_BothDescend перестанет ловить эту поломку. При смене pillars_TwoHeightsOverFlatGround_BothDescend проверь это утверждение.");
        assertTrue(
                high.end().y() <= 1,
                "путь со второго столба обязан выйти на землю, а кончается в " + high.end()
                        + " Иначе тест pillars_TwoHeightsOverFlatGround_BothDescend перестанет ловить эту поломку. При смене pillars_TwoHeightsOverFlatGround_BothDescend проверь это утверждение.");
    }

    @Test
    void pillar_TooDeepForEscape_StillNoPath() {
        NavTestWorld world = NavTestWorld.flat(0);
        for (int y = 1; y <= 16; y++) {
            world.solid(0, y, 0);
        }
        NavResult result = planAt(world, new BlockCoord(0, 17, 0), walkOnly(), new NavGoalBlock(8, 1, 0));

        assertFalse(
                result.found(),
                "со столба выше безопасного спуска путь не выдумывается: падение с 16 блоков убило бы духа, "
                        + "а не спасло"
                        + " Иначе тест pillar_TooDeepForEscape_StillNoPath перестанет ловить эту поломку. При смене pillar_TooDeepForEscape_StillNoPath проверь это утверждение.");
    }

    @Test
    void cave_UnderSettlementWithWater_PathStaysAboveGround() {
        NavTestWorld world = underWallCorridor(true);
        NavResult result = plan(world, 0, walkOnly(), new NavGoalBlock(10, 1, 0));

        assertTrue(
                result.path() == null || result.path().positions().stream().allMatch(cell -> cell.y() >= 1),
                "под поселением пещера с водой без выхода: путь не спускается туда, где вода держит духа — "
                        + (result.path() == null ? "путь пуст" : result.path().positions())
                        + " Иначе тест cave_UnderSettlementWithWater_PathStaysAboveGround перестанет ловить эту поломку. При смене cave_UnderSettlementWithWater_PathStaysAboveGround проверь это утверждение.");
    }
}
