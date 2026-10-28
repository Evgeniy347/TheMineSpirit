package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class SelfEscapeTests {
    private static final BlockCoord FEET = new BlockCoord(10, 64, 10);

    @Test
    void plan_OpenCell_IsEmpty() {
        Set<BlockCoord> air = Set.of(new BlockCoord(11, 64, 10), new BlockCoord(11, 65, 10));

        assertTrue(
                SelfEscape.plan(FEET, new BlockCoord(20, 64, 10), grid(air, (x, z) -> 64), true, 0)
                        .isEmpty(),
                "из клетки с проходом дух выйдет сам: план самоспасения не нужен");
    }

    @Test
    void plan_WalledEastWithTool_DigsTwoBlocksTowardGoal() {
        SelfEscape.Plan plan = SelfEscape.plan(FEET, new BlockCoord(20, 64, 10), grid(Set.of(), (x, z) -> 64), true, 0)
                .orElseThrow();

        assertEquals(SelfEscape.Method.DIG, plan.method(), "с киркой застрявший дух прокапывает проход сам");
        assertEquals(new BlockCoord(11, 64, 10), plan.target(), "прокапывать надо в сторону цели движения");
        assertEquals(
                List.of(new BlockCoord(11, 64, 10), new BlockCoord(11, 65, 10)),
                plan.blocks(),
                "проход в два блока высотой — сломать ноги и голову клетки впереди");
        assertEquals(0, plan.rise(), "горизонтальный проход не поднимает духа");
    }

    @Test
    void plan_WalledHeadOpen_DigsOneBlock() {
        Set<BlockCoord> air = Set.of(new BlockCoord(11, 65, 10));

        SelfEscape.Plan plan = SelfEscape.plan(FEET, new BlockCoord(20, 64, 10), grid(air, (x, z) -> 64), true, 0)
                .orElseThrow();

        assertEquals(
                List.of(new BlockCoord(11, 64, 10)),
                plan.blocks(),
                "если голова уже проходит, ломается только блок на уровне ног");
    }

    @Test
    void plan_WalledNoTool_IsEmpty() {
        assertTrue(
                SelfEscape.plan(FEET, new BlockCoord(20, 64, 10), grid(Set.of(), (x, z) -> 64), false, 0)
                        .isEmpty(),
                "без инструмента дух не прокапывает стену: его обязан выручить другой дух");
    }

    @Test
    void plan_GoalWest_DigsWest() {
        SelfEscape.Plan plan = SelfEscape.plan(FEET, new BlockCoord(0, 64, 10), grid(Set.of(), (x, z) -> 64), true, 0)
                .orElseThrow();

        assertEquals(new BlockCoord(9, 64, 10), plan.target(), "сторона копания выбирается по цели движения");
        assertEquals(
                List.of(new BlockCoord(9, 64, 10), new BlockCoord(9, 65, 10)),
                plan.blocks(),
                "блоки ломаются в той же стороне, куда идёт дух");
    }

    @Test
    void plan_DeepPitWithBlocks_PlacesSteps() {
        SelfEscape.Plan plan = SelfEscape.plan(FEET, new BlockCoord(10, 70, 10), pit(), false, 3)
                .orElseThrow();

        assertEquals(SelfEscape.Method.STEP, plan.method(), "с блоками в инвентаре дух поднимается ступенями");
        assertEquals(2, plan.rise(), "из ямы в три блока подъём на два");
        assertTrue(plan.blocks().isEmpty(), "ступени не ломают блоки");
    }

    @Test
    void plan_DeepPitWithoutBlocksOrTool_IsEmpty() {
        assertTrue(
                SelfEscape.plan(FEET, new BlockCoord(10, 70, 10), pit(), false, 1)
                        .isEmpty(),
                "ни ступеней, ни кирки — сам дух не выйдет, нужно задание спасения");
    }

    @Test
    void plan_DeepPitWithTool_DigsUpward() {
        SelfEscape.Plan plan = SelfEscape.plan(FEET, new BlockCoord(10, 70, 10), pit(), true, 1)
                .orElseThrow();

        assertEquals(SelfEscape.Method.DIG, plan.method(), "с киркой дух прокапывает потолок ямы");
        assertEquals(2, plan.rise(), "план помнит, на сколько надо подняться к краю");
        assertEquals(
                List.of(new BlockCoord(10, 65, 10), new BlockCoord(10, 66, 10)),
                plan.blocks(),
                "из ямы ломаются два блока над головой — проход к поверхности");
    }

    @Test
    void plan_StepWithoutRise_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SelfEscape.Plan(SelfEscape.Method.STEP, FEET, 0, List.of()),
                "ступень без подъёма обязана отклоняться: такой план не меняет положение духа");
    }

    private static StuckSituation.Grid pit() {
        return grid(Set.of(), (x, z) -> x == 10 && z == 10 ? 64 : 67);
    }

    private static StuckSituation.Grid grid(Set<BlockCoord> air, IntBinaryOperator floor) {
        Set<BlockCoord> cells = new HashSet<>(air);
        return new StuckSituation.Grid() {
            @Override
            public boolean passable(BlockCoord cell) {
                return cells.contains(cell);
            }

            @Override
            public int floorY(int x, int z) {
                return floor.applyAsInt(x, z);
            }
        };
    }
}
