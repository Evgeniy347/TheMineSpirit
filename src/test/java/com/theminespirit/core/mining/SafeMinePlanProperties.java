package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class SafeMinePlanProperties {
    private static final DescentDirection EAST = DescentDirection.EAST;

    @Property(tries = 150)
    void staircase_SafeShaft_StepsStayContinuous(
            @ForAll @IntRange(min = 1, max = 40) int depth, @ForAll @IntRange(min = -10, max = 60) int entryY) {
        int targetY = entryY - depth;
        if (entryY > SafeMinePlan.WORLD_MAX_Y || targetY < SafeMinePlan.WORLD_MIN_Y) {
            return;
        }
        BlockCoord entry = new BlockCoord(3, entryY, -5);
        CellWorld world = new CellWorld();
        for (int floor = entryY - 1; floor >= targetY; floor--) {
            world.set(3 + (entryY - 1 - floor), floor, -5, Cell.SOLID);
        }

        SafeMinePlan plan = SafeMinePlan.staircase(world, entry, EAST, targetY, 4);

        assertEquals(
                depth,
                plan.steps().size(),
                "Число шагов лесенки обязано равняться глубине спуска (этаж входа минус целевой): "
                        + "иначе дух либо стоит рано, либо копает под дно мира");
        assertEquals(DescentStop.TARGET_Y, plan.stop(), "Безопасная шахта обязана заканчиваться целью");
        for (int index = 1; index < plan.steps().size(); index++) {
            BlockCoord previous = plan.steps().get(index - 1).stand();
            BlockCoord stand = plan.steps().get(index).stand();
            assertEquals(previous.x() + EAST.dx, stand.x(), "Ступень обязана смещаться на блок по направлению");
            assertEquals(previous.y() - 1, stand.y(), "Ступень обязана опускаться ровно на один этаж");
            assertEquals(previous.z() + EAST.dz, stand.z(), "Ступень обязана держать координату поперёк направления");
        }
    }

    @Property(tries = 150)
    void tunnel_SafeTunnel_HasExactlyRequestedLength(@ForAll @IntRange(min = 1, max = 30) int length) {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = new CellWorld();
        for (int index = 0; index < length; index++) {
            world.set(index, 9, 0, Cell.SOLID);
        }

        SafeMinePlan plan = SafeMinePlan.tunnel(world, entry, EAST, length, 4);

        assertEquals(length, plan.steps().size(), "Штольня обязана быть ровно заданной длины без простоев");
        for (SafeMineStep step : plan.steps()) {
            assertEquals(entry.y(), step.stand().y(), "Штольня обязана держать этаж входа по всей длине");
        }
    }

    @Property(tries = 150)
    void tunnel_FluidInSideCell_AlwaysBecomesFillNotDanger(
            @ForAll @IntRange(min = 0, max = 9) int index, @ForAll @IntRange(min = 0, max = 1) int side) {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = new CellWorld();
        for (int column = 0; column < 10; column++) {
            world.set(column, 9, 0, Cell.SOLID);
        }
        BlockCoord hazard = new BlockCoord(index, 10, side == 0 ? 1 : -1);
        world.set(hazard.x(), hazard.y(), hazard.z(), Cell.LAVA);

        SafeMinePlan plan = SafeMinePlan.tunnel(world, entry, EAST, 10, 4);

        assertEquals(
                DescentStop.TARGET_Y,
                plan.stop(),
                "Лава в боковой стенке обязана закладываться, а не останавливать штольню");
        SafeMineStep step = plan.steps().get(index);
        assertEquals(
                true, step.fill().contains(hazard), "Опасная боковая клетка обязана попасть в заделки своего шага");
    }
}
