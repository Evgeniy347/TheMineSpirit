package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.DropPolicy;
import org.junit.jupiter.api.Test;

class MineReachTests {
    private static final int LEVEL = 0;
    private static final int FEET = LEVEL + 1;

    @Test
    void canReach_WallBetween_False() {
        CellWorld world = ground().fill(LEVEL + 1, 1, -6, 1, 6, Cell.SOLID)
                .fill(LEVEL + 2, 1, -6, 1, 6, Cell.SOLID)
                .fill(LEVEL + 3, 1, -6, 1, 6, Cell.SOLID);

        assertFalse(
                MineReach.canReach(new BlockCoord(2, FEET, 0), new BlockCoord(0, FEET, 0), world),
                "Стена в три блока между духом и стоянкой делает её недостижимой: дух не прыгает на три блока "
                        + "и не проходит сквозь породу, иначе подход кончается у стены и шахта встаёт");
    }

    @Test
    void canReach_StepUpOne_True() {
        CellWorld world = ground().set(0, LEVEL + 1, 0, Cell.SOLID);

        assertTrue(
                MineReach.canReach(new BlockCoord(1, FEET, 0), new BlockCoord(0, FEET + 1, 0), world),
                "Ступень на блок выше достижима прыжком: без этого дух не поднимается по лестнице шахты");
    }

    @Test
    void canReach_FallWithinLimit_True() {
        CellWorld world = ground().set(0, LEVEL, 0, Cell.PASSABLE)
                .set(0, LEVEL - 1, 0, Cell.PASSABLE)
                .set(0, LEVEL - 2, 0, Cell.SOLID);

        assertTrue(
                MineReach.canReach(new BlockCoord(1, FEET, 0), new BlockCoord(0, LEVEL - 1, 0), world),
                "Падение на два блока в яму у ступени достижимо: дух спускается в проход, а не обходит его");
    }

    @Test
    void canReach_FallTooDeep_False() {
        CellWorld world = ground().set(0, LEVEL, 0, Cell.PASSABLE)
                .set(0, LEVEL - 1, 0, Cell.PASSABLE)
                .set(0, LEVEL - 2, 0, Cell.PASSABLE)
                .set(0, LEVEL - 3, 0, Cell.PASSABLE)
                .set(0, LEVEL - 4, 0, Cell.SOLID);

        assertFalse(
                MineReach.canReach(new BlockCoord(1, FEET, 0), new BlockCoord(0, LEVEL - 3, 0), world),
                "Падение глубже " + MineReach.MAX_FALL + " блоков в яму не считается подходом: путь в навигации "
                        + "кончается на краю, и стоянка на дне подводит духа к провалу");
    }

    @Test
    void canReach_FallOfThreeIntoDeadEndPit_False() {
        CellWorld world = ground().set(0, LEVEL, 0, Cell.PASSABLE)
                .set(0, LEVEL - 1, 0, Cell.PASSABLE)
                .set(0, LEVEL - 2, 0, Cell.PASSABLE)
                .set(0, LEVEL - 3, 0, Cell.SOLID);
        BlockCoord from = new BlockCoord(1, FEET, 0);
        BlockCoord pitStand = new BlockCoord(0, LEVEL - 2, 0);

        assertFalse(
                MineReach.canReach(from, pitStand, world),
                "Падение на 3 блока в тупиковую яму не считается подходом: навигация духа роняет не глубже "
                        + DropPolicy.MAX_DROP + " блоков, поэтому путь заканчивается у духа, а стоянка" + pitStand
                        + " на дне — «за преградой» (observe6: яма (-189,67,-516), стоянка (-189,68,-517))");

        world.set(0, LEVEL - 1, 0, Cell.SOLID).set(0, LEVEL - 2, 0, Cell.SOLID);
        assertTrue(
                MineReach.canReach(from, new BlockCoord(0, LEVEL, 0), world),
                "Спуск на допустимую глубину " + DropPolicy.MAX_DROP
                        + " остаётся достижимым: иначе шахта не спускается по ступеням");
    }

    private static CellWorld ground() {
        return new CellWorld().fill(LEVEL, -4, -4, 4, 4, Cell.SOLID);
    }
}
