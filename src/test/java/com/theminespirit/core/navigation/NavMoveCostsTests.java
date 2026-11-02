// lib: Из Baritone (github.com/cabaletta/baritone, pathing/movement/movements/*), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NavMoveCostsTests {
    private static final double EPS = 1e-9;

    @Test
    void traverse_FlatGround_CostsOneWalkStep() {
        NavMoveResult move = traverse(NavTestWorld.flat(0), 64, 0, 1, 0, 1, 0);
        assertEquals(NavActionCosts.WALK_ONE_BLOCK_COST, move.cost, EPS, "шаг по ровному — цена ходьбы");
        assertEquals(1, move.x, "ход заканчивается в соседней клетке");
    }

    @Test
    void traverse_Water_CostsSwimStep() {
        NavMoveResult move = traverse(NavTestWorld.flat(0).wet(1, 1, 0).wet(1, 2, 0), 64, 0, 1, 0, 1, 0);
        assertEquals(NavActionCosts.WALK_ONE_IN_WATER_COST, move.cost, EPS, "в воде цена плавания");
    }

    @Test
    void traverse_BreakableWall_AddsMiningToWalk() {
        NavTestWorld world = NavTestWorld.flat(0).breakable(1, 1, 0).breakable(1, 2, 0);
        NavMoveResult move = traverse(world, 64, 0, 1, 0, 1, 0);
        assertEquals(
                NavActionCosts.WALK_ONE_BLOCK_COST + 2 * (10 + 2),
                move.cost,
                EPS,
                "ломка тела и головы входит в цену хода");
    }

    @Test
    void traverse_UnbreakableWall_IsImpossible() {
        NavTestWorld world = NavTestWorld.flat(0).solid(1, 1, 0).solid(1, 2, 0);
        NavMoveResult move = traverse(world, 64, 0, 1, 0, 1, 0);
        assertTrue(move.cost >= NavActionCosts.COST_INF, "неразрушимая стена не проходится");
    }

    @Test
    void traverse_MissingFloor_BridgesWithBlock() {
        NavTestWorld world = NavTestWorld.flat(0).air(1, 0, 0);
        NavMoveResult move = traverse(world, 64, 0, 1, 0, 1, 0);
        assertEquals(
                NavActionCosts.WALK_ONE_BLOCK_COST + NavSettings.defaults().placeCost(),
                move.cost,
                EPS,
                "мост из блока стоит ходьбу и установку");
        NavMoveResult withoutBlocks = traverse(world, 0, 0, 1, 0, 1, 0);
        assertTrue(withoutBlocks.cost >= NavActionCosts.COST_INF, "без блоков мост не построить");
    }

    @Test
    void ascend_OneBlockStep_AddsJump() {
        NavTestWorld world = NavTestWorld.flat(0).solid(1, 1, 0);
        NavMoveResult move = ascend(world, 64, 0, 1, 0, 1, 0);
        double expected = NavActionCosts.WALK_ONE_BLOCK_COST
                + NavActionCosts.JUMP_ONE_BLOCK_COST
                + NavSettings.defaults().jumpPenalty();
        assertEquals(expected, move.cost, EPS, "подъём на блок — ходьба, прыжок и надбавка");
        assertEquals(2, move.y, "подъём заканчивается на блок выше");
    }

    @Test
    void descend_OneBlockDrop_UsesWalkOffAndFall() {
        NavMoveResult move = descend(NavTestWorld.flat(0), 64, 0, 2, 0, 1, 0);
        double expected = NavActionCosts.WALK_OFF_BLOCK_COST
                + NavActionCosts.fallNBlocksCost(1)
                + NavActionCosts.CENTER_AFTER_FALL_COST;
        assertEquals(expected, move.cost, EPS, "спуск на блок — шаг с края и падение");
        assertEquals(1, move.y, "спуск заканчивается на блок ниже");
    }

    @Test
    void pillar_NoBlockOrBlockedHead_IsImpossible() {
        NavTestWorld world = NavTestWorld.flat(0);
        NavMoveResult withBlocks = pillar(world, 64, 0, 1, 0);
        assertEquals(2, withBlocks.y, "столб поднимает на блок");
        assertTrue(withBlocks.cost < NavActionCosts.COST_INF, "с блоком столб возможен");
        assertTrue(pillar(world, 0, 0, 1, 0).cost >= NavActionCosts.COST_INF, "без блока столб невозможен");
        NavTestWorld blocked = NavTestWorld.flat(0).solid(0, 3, 0);
        assertTrue(pillar(blocked, 64, 0, 1, 0).cost >= NavActionCosts.COST_INF, "твёрдый блок над головой мешает");
    }

    @Test
    void downward_BreakableFloor_DigsOneBlock() {
        NavTestWorld world = NavTestWorld.flat(0).breakable(0, 0, 0);
        NavMoveResult move = downward(world, 64, 0, 1, 0);
        assertEquals(0, move.y, "копание вниз опускает на блок");
        assertEquals(
                NavActionCosts.fallNBlocksCost(1) + 10 + NavSettings.defaults().breakPenalty(),
                move.cost,
                EPS,
                "копание вниз — падение и ломка");
        NavMoveResult unbreakable = downward(NavTestWorld.flat(0), 64, 0, 1, 0);
        assertTrue(unbreakable.cost >= NavActionCosts.COST_INF, "неразрушимый пол не копается");
    }

    @Test
    void fall_DeepDrop_RespectsMaxFallAndWater() {
        NavMoveResult shallow = fall(NavTestWorld.flat(0), 64, 0, 3, 0, 1, 0);
        assertTrue(shallow.cost < NavActionCosts.COST_INF, "падение на 2 блока допустимо");
        assertEquals(1, shallow.y, "приземление на пол");
        NavMoveResult deep = fall(NavTestWorld.flat(0), 64, 0, 5, 0, 1, 0);
        assertTrue(deep.cost >= NavActionCosts.COST_INF, "падение на 4 блока без воды запрещено");
        NavTestWorld water =
                NavTestWorld.flat(0).wet(1, 1, 0).wet(1, 2, 0).wet(1, 3, 0).wet(1, 4, 0);
        NavMoveResult intoWater = fall(water, 64, 0, 5, 0, 1, 0);
        assertTrue(intoWater.cost < NavActionCosts.COST_INF, "в воду падать можно глубже");
    }

    @Test
    void jump_TwoBlockGap_CrossesWithoutPlacing() {
        NavTestWorld world = NavTestWorld.flat(0).air(1, 0, 0);
        NavMoveResult move = jump(world, 0, 1, 0, 1, 0);
        assertEquals(2, move.x, "прыжок переносит через щель на две клетки");
        assertEquals(
                2 * NavActionCosts.WALK_ONE_BLOCK_COST + NavActionCosts.JUMP_ONE_BLOCK_COST,
                move.cost,
                EPS,
                "прыжок стоит два шага и прыжок, блоки не ставятся");
    }

    @Test
    void jump_FiveBlockGap_IsImpossible() {
        NavTestWorld world =
                NavTestWorld.flat(0).air(1, 0, 0).air(2, 0, 0).air(3, 0, 0).air(4, 0, 0);
        assertTrue(
                jump(world, 0, 1, 0, 1, 0).cost >= NavActionCosts.COST_INF, "щель шире четырёх блоков не перепрыгнуть");
    }

    @Test
    void pillar_InWater_SwimsUp() {
        NavTestWorld world = NavTestWorld.flat(0).wet(0, 1, 0).wet(0, 2, 0);
        NavMoveResult move = pillar(world, 0, 0, 1, 0);
        assertEquals(2, move.y, "в воде столб заменяется подъёмом вплавь");
        assertEquals(NavActionCosts.WALK_ONE_IN_WATER_COST, move.cost, EPS, "подъём в воде стоит плавание");
    }

    private static NavMoveResult traverse(NavTestWorld world, int blocks, int x, int y, int z, int dx, int dz) {
        return apply(NavMoveCosts.Kind.TRAVERSE, world, blocks, x, y, z, dx, dz);
    }

    private static NavMoveResult ascend(NavTestWorld world, int blocks, int x, int y, int z, int dx, int dz) {
        return apply(NavMoveCosts.Kind.ASCEND, world, blocks, x, y, z, dx, dz);
    }

    private static NavMoveResult descend(NavTestWorld world, int blocks, int x, int y, int z, int dx, int dz) {
        return apply(NavMoveCosts.Kind.DESCEND, world, blocks, x, y, z, dx, dz);
    }

    private static NavMoveResult apply(
            NavMoveCosts.Kind kind, NavTestWorld world, int blocks, int x, int y, int z, int dx, int dz) {
        NavMoveResult out = new NavMoveResult();
        out.reset(x, y, z);
        NavContext context = new NavContext(world, NavSettings.defaults(), blocks);
        switch (kind) {
            case TRAVERSE -> NavMoveCosts.traverse(context, x, y, z, dx, dz, out);
            case ASCEND -> NavMoveCosts.ascend(context, x, y, z, dx, dz, out);
            case DESCEND -> NavMoveCosts.descend(context, x, y, z, dx, dz, out);
            case DIAGONAL -> NavMoveCosts.diagonal(context, x, y, z, dx, dz, out);
            case PILLAR -> NavMoveCosts.pillar(context, x, y, z, out);
            case DOWNWARD -> NavMoveCosts.downward(context, x, y, z, out);
        }
        return out;
    }

    private static NavMoveResult pillar(NavTestWorld world, int blocks, int x, int y, int z) {
        NavMoveResult out = new NavMoveResult();
        out.reset(x, y, z);
        NavMoveCosts.pillar(new NavContext(world, NavSettings.defaults(), blocks), x, y, z, out);
        return out;
    }

    private static NavMoveResult downward(NavTestWorld world, int blocks, int x, int y, int z) {
        NavMoveResult out = new NavMoveResult();
        out.reset(x, y, z);
        NavMoveCosts.downward(new NavContext(world, NavSettings.defaults(), blocks), x, y, z, out);
        return out;
    }

    private static NavMoveResult fall(NavTestWorld world, int blocks, int x, int y, int z, int dx, int dz) {
        NavMoveResult out = new NavMoveResult();
        out.reset(x, y, z);
        NavMoveCosts.fall(new NavContext(world, NavSettings.defaults(), blocks), x, y, z, dx, dz, out);
        return out;
    }

    private static NavMoveResult jump(NavTestWorld world, int x, int y, int z, int dx, int dz) {
        NavMoveResult out = new NavMoveResult();
        out.reset(x, y, z);
        NavMoveCosts.jump(new NavContext(world, NavSettings.defaults(), 0), x, y, z, dx, dz, out);
        return out;
    }
}
