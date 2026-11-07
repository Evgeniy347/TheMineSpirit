package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PitExitGroupTests {
    private static final BlockCoord FEET = PitTestWorld.FEET;

    @Test
    void plan_TwoSpiritsInOneByOne_DoNotBreakUnderEachOther() {
        BlockCoord upper = new BlockCoord(0, 65, 0);
        PitExitPlanner.World world = PitTestWorld.pit(5, 0, PitExitPlanner.Kind.SOFT);

        PitExitPlanner.Plan lower =
                PitExitPlanner.plan(FEET, world, new PitExitPlanner.Supplies(0, false), Set.of(upper));
        PitExitPlanner.Plan top =
                PitExitPlanner.plan(upper, world, new PitExitPlanner.Supplies(0, false), Set.of(FEET));

        assertTrue(lower.possible(), "Нижний дух обязан найти выход, а не ждать, пока верхний уйдёт");
        assertTrue(top.possible(), "Верхний дух тоже обязан найти выход");
        assertFalse(
                lower.dig().contains(FEET) || lower.dig().contains(PitTestWorld.below(upper)),
                "Нижний не ломает блок под соседом: сосед не проваливается");
        assertFalse(top.dig().contains(FEET), "Верхний не занимает и не ломает клетку нижнего");
    }

    @Test
    void plan_ThreeSpiritsInTwoByTwo_AllEscapeWithoutCrossing() {
        BlockCoord a = new BlockCoord(0, 64, 0);
        BlockCoord b = new BlockCoord(1, 64, 0);
        BlockCoord c = new BlockCoord(0, 64, 1);
        PitExitPlanner.World world = PitTestWorld.pit(6, 1, PitExitPlanner.Kind.SOFT);
        Set<BlockCoord> occupied = Set.of(a, b, c);

        for (BlockCoord feet : occupied) {
            PitExitPlanner.Plan plan = PitExitPlanner.plan(
                    feet, world, new PitExitPlanner.Supplies(0, false), PitTestWorld.without(occupied, feet));

            assertTrue(plan.possible(), "Каждый дух в яме 2×2 получает свой выход, а не ждёт освобождения");
            for (BlockCoord other : PitTestWorld.without(occupied, feet)) {
                assertFalse(
                        plan.dig().contains(other) || plan.dig().contains(PitTestWorld.below(other)),
                        "План " + feet + " не ломает клетку соседа " + other + " и блок под ним");
            }
        }
    }

    @Test
    void plan_ThreeSpiritsInThreeByThree_AllEscape() {
        BlockCoord a = new BlockCoord(-1, 64, -1);
        BlockCoord b = new BlockCoord(0, 64, -1);
        BlockCoord c = new BlockCoord(-1, 64, 0);
        PitExitPlanner.World world = PitTestWorld.pit(6, 2, PitExitPlanner.Kind.SOFT);
        Set<BlockCoord> occupied = Set.of(a, b, c);

        for (BlockCoord feet : occupied) {
            PitExitPlanner.Plan plan = PitExitPlanner.plan(
                    feet, world, new PitExitPlanner.Supplies(0, false), PitTestWorld.without(occupied, feet));
            assertTrue(plan.possible(), "В яме 3×3 каждый дух выходит сам");
        }
    }

    @Test
    void plan_SpiritUnderNeighbour_LeavesSupportBlockAlone() {
        BlockCoord lower = new BlockCoord(0, 64, 0);
        BlockCoord upper = new BlockCoord(0, 66, 0);
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                lower,
                PitTestWorld.pit(8, 0, PitExitPlanner.Kind.SOFT),
                new PitExitPlanner.Supplies(0, false),
                Set.of(upper));

        assertFalse(
                plan.dig().contains(PitTestWorld.below(upper)),
                "Блок под ногами верхнего духа — его опора: нижний не выбивает её из-под соседа");
        assertTrue(plan.possible(), "Нижний всё равно находит выход в сторону");
    }

    @Test
    void plan_NeighbourCell_IsNeverDug() {
        BlockCoord neighbour = new BlockCoord(1, 64, 0);
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET,
                PitTestWorld.pit(6, 1, PitExitPlanner.Kind.SOFT),
                new PitExitPlanner.Supplies(0, false),
                Set.of(neighbour));

        assertTrue(plan.possible(), "Сосед не лишает духа выхода: он выбирает другую сторону");
        assertFalse(plan.dig().contains(neighbour), "Клетка соседа не ломается");
    }

    @Test
    void plan_LiveWarehousePit_SpiritEscapesWithoutBlocks() throws IOException {
        PitExitPlanner.Plan plan =
                PitExitPlanner.plan(PitTestWorld.LIVE_FEET, PitTestWorld.live(), new PitExitPlanner.Supplies(0, false));

        assertTrue(plan.possible(), "Живая яма под складом (-180,57,-519): дух обязан выйти сам, без блоков и кирки");
        assertEquals(
                12,
                plan.exit().y() - PitTestWorld.LIVE_FEET.y(),
                "Выход ведёт на пол склада (уровень 69): дух поднимается из ямы, а не остаётся на дне");
        assertFalse(plan.restore().isEmpty(), "После выхода в складе не остаётся дыры: клетки на засыпку названы");
    }

    @Test
    void plan_LiveWarehousePit_ThreeSpiritsEscapeWithoutDiggingUnderEachOther() throws IOException {
        PitExitPlanner.World world = PitTestWorld.live();
        Set<BlockCoord> occupied = Set.of(PitTestWorld.LIVE_FEET, new BlockCoord(-1, 0, 0), new BlockCoord(0, 0, 1));

        for (BlockCoord feet : occupied) {
            PitExitPlanner.Plan plan = PitExitPlanner.plan(
                    feet, world, new PitExitPlanner.Supplies(0, false), PitTestWorld.without(occupied, feet));
            assertTrue(plan.possible(), "Живые три духа в яме: " + feet + " выходит сам, не ожидая спасателя");
            for (BlockCoord other : PitTestWorld.without(occupied, feet)) {
                assertFalse(
                        plan.dig().contains(other) || plan.dig().contains(PitTestWorld.below(other)),
                        "План " + feet + " не ломает клетку соседа " + other + " и блок под ним");
            }
        }
    }
}
