package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class DescentPlanProperties {
    private static final DescentDirection EAST = DescentDirection.EAST;

    @Property(tries = 200)
    void from_SafeShaft_StepCountEqualsEntryFloorMinusTargetYPlusOne(
            @ForAll @IntRange(min = -20, max = 50) int targetY,
            @ForAll @IntRange(min = 1, max = 60) int depth) {
        int entryY = targetY + depth;
        if (entryY > DescentPlan.WORLD_MAX_Y || entryY < DescentPlan.WORLD_MIN_Y + 1) {
            return;
        }
        BlockCoord entry = new BlockCoord(0, entryY, 0);
        CellWorld world = DescentPlanTests.solidDescent(new CellWorld(), 0, 0, EAST, entryY - 1, targetY);

        DescentPlan plan = DescentPlan.from(world, entry, EAST, targetY, 4);

        assertEquals(depth, plan.steps().size(),
                "Число шагов обязано равняться глубине спуска (этаж под ногами минус целевой): "
                        + "иначе дух либо останавливается рано, либо копает под дно мира");
        assertEquals(DescentStop.TARGET_Y, plan.stop(), "Безопасная шахта обязана заканчиваться целью");
    }

    @Property(tries = 200)
    void from_LavaAtSideOfLowestPassageCellOfStep_StopsExactlyBeforeThatStep(
            @ForAll @IntRange(min = 0, max = 5) int lavaStep,
            @ForAll @IntRange(min = 6, max = 20) int entryY) {
        int targetY = entryY - 6;
        int sideY = entryY - lavaStep;
        BlockCoord entry = new BlockCoord(0, entryY, 0);
        CellWorld world = DescentPlanTests.solidDescent(new CellWorld(), 0, 0, EAST, entryY - 1, targetY)
                .set(1, sideY, -1, Cell.LAVA);

        DescentPlan plan = DescentPlan.from(world, entry, EAST, targetY, 4);

        assertEquals(lavaStep, plan.steps().size(),
                "Лава у боковой стенки шага k обязана обрезать план ровно перед шагом k: дальше копать нельзя");
        assertEquals(DescentStop.DANGER, plan.stop(), "Лава — остановка по опасности");
        assertTrue(plan.dangerReason().contains("лава"),
                "Причина остановки обязана называть лаву, чтобы игрок понял причину");
    }

    @Property(tries = 200)
    void from_Steps_StayInEntryColumnAndOffsetByDirection(
            @ForAll @IntRange(min = 1, max = 8) int depth,
            @ForAll @IntRange(min = -20, max = 60) int entryY) {
        int targetY = entryY - depth;
        if (entryY > DescentPlan.WORLD_MAX_Y || targetY < DescentPlan.WORLD_MIN_Y) {
            return;
        }
        BlockCoord entry = new BlockCoord(3, entryY, -5);
        CellWorld world = DescentPlanTests.solidDescent(new CellWorld(), 3, -5, EAST, entryY - 1, targetY);

        DescentPlan plan = DescentPlan.from(world, entry, EAST, targetY, 4);

        for (DescentStep step : plan.steps()) {
            assertEquals(new BlockCoord(3, step.stand().y(), -5), step.stand(),
                    "Ступень обязана оставаться в колонке входа: дух спускается по вертикали, а не гуляет");
            for (BlockCoord cell : step.passage()) {
                assertEquals(4, cell.x(), "Клетка прохода обязана быть на блок ближе по направлению спуска");
                assertEquals(-5, cell.z(), "Клетка прохода обязана сохранять координату колонки по Z");
            }
        }
    }
}
