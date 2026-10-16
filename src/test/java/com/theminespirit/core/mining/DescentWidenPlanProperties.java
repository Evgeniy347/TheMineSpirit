package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class DescentWidenPlanProperties {
    private static final DescentDirection EAST = DescentDirection.EAST;

    @Property(tries = 200)
    void from_SafeShaft_EveryStepHasWidthTimesHeightCells(
            @ForAll @IntRange(min = 3, max = 7) int width,
            @ForAll @IntRange(min = 6, max = 40) int entryY,
            @ForAll @IntRange(min = 1, max = 6) int depth) {
        int targetY = entryY - depth;
        if (targetY < DescentPlan.WORLD_MIN_Y) {
            return;
        }
        if (width % 2 == 0) {
            return;
        }
        CellWorld world = DescentWidenPlanTests.widenWorld(0, 0, EAST, entryY - 1, targetY, width);
        DescentPlan level1 = DescentWidenPlanTests.level1(world, new BlockCoord(0, entryY, 0), EAST, targetY);

        DescentWidenPlan plan = DescentWidenPlan.from(world, level1, width, 6);

        assertEquals(depth, plan.steps().size(),
                "Число шагов расширения обязано равняться глубине спуска: расширяют весь существующий спуск");
        for (DescentWidenStep step : plan.steps()) {
            assertEquals(width * 6, step.cells().size(),
                    "Каждый шаг обязан нести " + width + "×6 клеток прохода: иначе тоннель не той ширины или высоты");
        }
    }

    @Property(tries = 200)
    void from_LavaAtWidenCell_StopsExactlyBeforeThatStep(
            @ForAll @IntRange(min = 0, max = 5) int stepIndex,
            @ForAll @IntRange(min = -1, max = 1) int lane,
            @ForAll @IntRange(min = 4, max = 6) int above,
            @ForAll @IntRange(min = 10, max = 40) int entryY) {
        int targetY = entryY - 6;
        if (targetY < DescentPlan.WORLD_MIN_Y) {
            return;
        }
        int floor = entryY - 1 - stepIndex;
        CellWorld world = DescentWidenPlanTests.widenWorld(0, 0, EAST, entryY - 1, targetY, 3)
                .set(stepIndex, floor + above, lane, Cell.LAVA);
        DescentPlan level1 = DescentWidenPlanTests.level1(world, new BlockCoord(0, entryY, 0), EAST, targetY);

        DescentWidenPlan plan = DescentWidenPlan.from(world, level1, 3, 6);

        assertEquals(stepIndex, plan.steps().size(),
                "Лава в клетке расширения шага k обязана обрезать план ровно перед шагом k: дальше копать нельзя");
        assertEquals(DescentStop.DANGER, plan.stop(), "Лава — остановка по опасности");
        assertTrue(plan.dangerReason().contains("лава"),
                "Причина остановки обязана называть лаву, чтобы игрок понял причину");
    }

    @Property(tries = 200)
    void from_SafeShaft_CellsStayWithinHeightAboveFloor(
            @ForAll @IntRange(min = 3, max = 7) int width,
            @ForAll @IntRange(min = 6, max = 40) int entryY,
            @ForAll @IntRange(min = 1, max = 6) int depth) {
        int targetY = entryY - depth;
        if (targetY < DescentPlan.WORLD_MIN_Y || width % 2 == 0) {
            return;
        }
        CellWorld world = DescentWidenPlanTests.widenWorld(0, 0, EAST, entryY - 1, targetY, width);
        DescentPlan level1 = DescentWidenPlanTests.level1(world, new BlockCoord(0, entryY, 0), EAST, targetY);

        DescentWidenPlan plan = DescentWidenPlan.from(world, level1, width, 6);

        for (DescentWidenStep step : plan.steps()) {
            for (BlockCoord cell : step.cells()) {
                assertTrue(cell.y() > step.floor() && cell.y() <= step.floor() + 6,
                        "Клетка расширения " + cell + " обязана быть на высотах floor+1..floor+6 над полом "
                                + step.floor());
            }
        }
    }
}
