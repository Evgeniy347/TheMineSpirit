package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class DescentWidenPlanTests {
    private static final BlockCoord ENTRY = new BlockCoord(0, 4, 0);
    private static final DescentDirection EAST = DescentDirection.EAST;
    private static final int WIDTH = DescentLevels.defaults().widenWidth();
    private static final int HEIGHT = DescentLevels.defaults().widenHeight();

    @Test
    void from_SafeShaft_BuildsThreeBySixCrossSectionPerStep() {
        CellWorld world = widenWorld(0, 0, EAST, 3, 1, WIDTH);

        DescentWidenPlan plan = DescentWidenPlan.from(world, level1(world, ENTRY, EAST, 1), WIDTH, HEIGHT);

        assertEquals(3, plan.steps().size(),
                "Расширение обязано пройти все шаги уровня 1: до рабочего уровня расширяют весь спуск");
        assertEquals(DescentStop.TARGET_Y, plan.stop(), "Безопасное расширение заканчивается достижением цели");
        assertEquals("", plan.dangerReason(), "До цели дошли без опасности — причины остановки нет");
        for (DescentWidenStep step : plan.steps()) {
            assertEquals(WIDTH * HEIGHT, step.cells().size(),
                    "Шаг обязан содержать ровно " + (WIDTH * HEIGHT) + " клеток: " + WIDTH + " в ширину и "
                            + HEIGHT + " в высоту");
            assertEquals(WIDTH, step.width(), "Ширина шага обязана совпадать с данными расширения");
            assertEquals(HEIGHT, step.height(), "Высота шага обязана совпадать с данными расширения");
        }
        DescentWidenStep first = plan.steps().get(0);
        assertEquals(new BlockCoord(0, 4, 0), first.stand(),
                "Ступень шага 0 — клетка входа: расширение начинается там же, где спуск");
        assertEquals(new BlockCoord(0, 3, 0), new BlockCoord(first.stand().x(), first.floor(), first.stand().z()),
                "Этаж шага 0 — блок под ногами духа");
        assertEquals(3, first.floor(),
                "Первый этаж расширения обязан быть полом входа: расширяют от пола уровня 1");
    }

    @Test
    void from_SafeShaft_CellsFormThreeLanesOfSixHeights() {
        CellWorld world = widenWorld(0, 0, EAST, 3, 1, WIDTH);

        DescentWidenPlan plan = DescentWidenPlan.from(world, level1(world, ENTRY, EAST, 1), WIDTH, HEIGHT);

        DescentWidenStep first = plan.steps().get(0);
        for (BlockCoord cell : first.cells()) {
            assertEquals(0, cell.x(), "При спуске на восток ширина ложится по Z: колонка расширения держит X");
            assertTrue(cell.z() >= -1 && cell.z() <= 1,
                    "Клетки обязаны занимать три полосы z=-1..1, фактически " + cell);
            assertTrue(cell.y() >= first.floor() + 1 && cell.y() <= first.floor() + HEIGHT,
                    "Клетки обязаны быть на высотах floor+1..floor+" + HEIGHT + ", фактически " + cell);
        }
        for (int lane = -1; lane <= 1; lane++) {
            for (int above = 1; above <= HEIGHT; above++) {
                assertTrue(first.cells().contains(new BlockCoord(0, first.floor() + above, lane)),
                        "Клетка полосы " + lane + " высоты floor+" + above + " обязана входить в расширение");
            }
        }
    }

    @Test
    void from_SafeShaft_StepCountEqualsLevel1StepCount() {
        CellWorld world = widenWorld(0, 0, EAST, 3, 1, WIDTH);

        DescentWidenPlan plan = DescentWidenPlan.from(world, level1(world, ENTRY, EAST, 1), WIDTH, HEIGHT);

        assertEquals(3, plan.steps().size(),
                "Шагов расширения ровно столько же, сколько шагов уровня 1: расширяют по всей длине существующего спуска");
    }

    @Test
    void from_LavaInSideCellOfLaterStep_StopsBeforeIt() {
        CellWorld world = widenWorld(0, 0, EAST, 3, 1, WIDTH).set(1, 3, 1, Cell.LAVA);

        DescentWidenPlan plan = DescentWidenPlan.from(world, level1(world, ENTRY, EAST, 1), WIDTH, HEIGHT);

        assertEquals(1, plan.steps().size(),
                "Перед лавой в боковой полосе расширение обязано обрезаться: шаг с лавой не выполняется");
        assertEquals(DescentStop.DANGER, plan.stop(), "Лава — остановка по опасности, а не достижение цели");
        assertTrue(plan.dangerReason().startsWith("опасность: "),
                "Причина обязана начинаться с 'опасность: ': по формату её узнают отчёт и игрок");
        assertTrue(plan.dangerReason().contains("лава"), "Причина обязана называть лаву");
        assertTrue(plan.dangerReason().contains("(1, 3, 1)"), "Причина обязана называть клетку с лавой");
    }

    @Test
    void from_SideFloorIsVoid_StopsWithVoidReason() {
        CellWorld world = widenWorld(0, 0, EAST, 3, 1, WIDTH).set(0, 3, 1, Cell.PASSABLE);

        DescentWidenPlan plan = DescentWidenPlan.from(world, level1(world, ENTRY, EAST, 1), WIDTH, HEIGHT);

        assertTrue(plan.steps().isEmpty(),
                "Нетвёрдый пол боковой полосы первого шага обязан остановить расширение до копания");
        assertEquals(DescentStop.DANGER, plan.stop(), "Пустота вместо пола — остановка по опасности");
        assertTrue(plan.dangerReason().contains("пустота"),
                "Причина обязана называть пустоту: игрок должен видеть, что пола нет");
    }

    @Test
    void from_FallingSideFloor_IsAcceptedAsSolid() {
        CellWorld world = widenWorld(0, 0, EAST, 3, 1, WIDTH).set(0, 3, 1, Cell.FALLING);

        DescentWidenPlan plan = DescentWidenPlan.from(world, level1(world, ENTRY, EAST, 1), WIDTH, HEIGHT);

        assertEquals(3, plan.steps().size(),
                "Сыпучий пол держит ноги духа: расширение обязано строиться как для твёрдого");
    }

    @Test
    void from_EvenWidth_Throws() {
        CellWorld world = widenWorld(0, 0, EAST, 3, 1, WIDTH);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DescentWidenPlan.from(world, level1(world, ENTRY, EAST, 1), 4, HEIGHT),
                "Чётная ширина обязана отклоняться: проход симметричен центральной колонке спуска");

        assertTrue(error.getMessage().contains("Ширина расширения"),
                "Отказ обязан называть ширину расширения");
    }

    @Test
    void from_HeightLessThanOne_Throws() {
        CellWorld world = widenWorld(0, 0, EAST, 3, 1, WIDTH);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DescentWidenPlan.from(world, level1(world, ENTRY, EAST, 1), WIDTH, 0),
                "Нулевая высота обязана отклоняться: расширять нечем");

        assertTrue(error.getMessage().contains("Высота расширения"),
                "Отказ обязан называть высоту расширения");
    }

    static DescentPlan level1(CellWorld world, BlockCoord entry, DescentDirection direction, int targetY) {
        return DescentPlan.from(world, entry, direction, targetY, 4);
    }

    static CellWorld widenWorld(int x, int z, DescentDirection direction, int entryFloor, int targetY, int width) {
        CellWorld world = DescentPlanTests.solidDescent(new CellWorld(), x, z, direction, entryFloor, targetY);
        int sideX = -direction.dz;
        int sideZ = direction.dx;
        int half = width / 2;
        int index = 0;
        for (int floor = entryFloor; floor >= targetY; floor--, index++) {
            int columnX = x + index * direction.dx;
            int columnZ = z + index * direction.dz;
            for (int lane = -half; lane <= half; lane++) {
                world.set(columnX + sideX * lane, floor, columnZ + sideZ * lane, Cell.SOLID);
                world.set(columnX + sideX * lane, floor - 1, columnZ + sideZ * lane, Cell.SOLID);
            }
        }
        return world;
    }
}
