package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class DescentStairsPlanProperties {
    private static final DescentDirection EAST = DescentDirection.EAST;

    @Property(tries = 200)
    void from_SafeShaft_StairsCoverStairWidthAndRailsStayFree(
            @ForAll @IntRange(min = 3, max = 7) int widenWidth,
            @ForAll @IntRange(min = 1, max = 6) int stairWidth,
            @ForAll @IntRange(min = 6, max = 40) int entryY,
            @ForAll @IntRange(min = 1, max = 6) int depth) {
        if (widenWidth % 2 == 0 || stairWidth > widenWidth - 1) {
            return;
        }
        int targetY = entryY - depth;
        if (targetY < DescentPlan.WORLD_MIN_Y) {
            return;
        }
        CellWorld world = DescentWidenPlanTests.widenWorld(0, 0, EAST, entryY - 1, targetY, widenWidth);
        DescentPlan level1 = DescentWidenPlanTests.level1(world, new BlockCoord(0, entryY, 0), EAST, targetY);

        DescentStairsPlan plan = DescentStairsPlan.from(world, level1, widenWidth, stairWidth);

        assertEquals(
                depth,
                plan.steps().size(),
                "Ступеньки обязаны пройти весь спуск уровня 1: по всей длине существующего спуска");
        for (DescentStairStep step : plan.steps()) {
            assertEquals(
                    stairWidth,
                    step.stairCells().size(),
                    "Ступеньки обязаны стоять ровно на " + stairWidth + " полосах ширины");
            assertEquals(
                    widenWidth - stairWidth,
                    step.railCells().size(),
                    "Рельсовых полос обязано остаться " + (widenWidth - stairWidth) + ": их не занимают ступеньками");
            Set<BlockCoord> all = new HashSet<>(step.stairCells());
            all.addAll(step.railCells());
            assertEquals(
                    widenWidth,
                    all.size(),
                    "Ступеньки и рельсовый проход обязаны вместе покрывать все полосы ширины без пересечений");
            for (BlockCoord rail : step.railCells()) {
                assertFalse(step.stairCells().contains(rail), "Полоса рельсов обязана остаться свободной от ступенек");
            }
            for (BlockCoord cell : all) {
                assertTrue(
                        cell.y() == step.floor() + 1,
                        "Клетка ступеньки или рельса " + cell + " обязана быть на высоте пола плюс один");
            }
        }
    }

    @Property(tries = 200)
    void from_LavaAtStairCell_StopsExactlyBeforeThatStep(
            @ForAll @IntRange(min = 0, max = 5) int stepIndex,
            @ForAll @IntRange(min = 1, max = 2) int side,
            @ForAll @IntRange(min = 10, max = 40) int entryY) {
        int targetY = entryY - 6;
        if (targetY < DescentPlan.WORLD_MIN_Y) {
            return;
        }
        int lane = side == 1 ? -1 : 1;
        int floor = entryY - 1 - stepIndex;
        CellWorld world = DescentWidenPlanTests.widenWorld(0, 0, EAST, entryY - 1, targetY, 3)
                .set(stepIndex, floor + 1, lane, Cell.LAVA);
        DescentPlan level1 = DescentWidenPlanTests.level1(world, new BlockCoord(0, entryY, 0), EAST, targetY);

        DescentStairsPlan plan = DescentStairsPlan.from(world, level1, 3, 2);

        assertEquals(
                stepIndex,
                plan.steps().size(),
                "Лава в полосе ступеньки шага k обязана обрезать план ровно перед шагом k: ступеньки не кладут в лаву");
        assertEquals(DescentStop.DANGER, plan.stop(), "Лава — остановка по опасности");
        assertTrue(
                plan.dangerReason().contains("лава"),
                "Причина остановки обязана называть лаву, чтобы игрок понял причину");
    }
}
