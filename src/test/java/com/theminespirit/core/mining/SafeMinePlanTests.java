package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class SafeMinePlanTests {
    private static final DescentDirection EAST = DescentDirection.EAST;

    private static CellWorld safeStaircase(BlockCoord entry, int targetY) {
        CellWorld world = new CellWorld();
        int entryFloor = entry.y() - 1;
        for (int floor = entryFloor; floor >= targetY; floor--) {
            world.set(entry.x() + (entryFloor - floor), floor, entry.z(), Cell.SOLID);
        }
        return world;
    }

    private static CellWorld safeTunnel(BlockCoord entry, int length) {
        CellWorld world = new CellWorld();
        int floor = entry.y() - 1;
        for (int index = 0; index < length; index++) {
            world.set(entry.x() + index, floor, entry.z(), Cell.SOLID);
        }
        return world;
    }

    @Test
    void staircase_SafeShaft_StepsAreContinuousDownAndForward() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeStaircase(entry, 1);

        SafeMinePlan plan = SafeMinePlan.staircase(world, entry, EAST, 1, 4);

        assertEquals(
                9,
                plan.steps().size(),
                "Спуск с этажа 9 до целевого 1 обязан состоять из девяти шагов: по одному на каждый этаж");
        assertEquals(DescentStop.TARGET_Y, plan.stop(), "Безопасная шахта без опасных клеток заканчивается целью");
        assertEquals("", plan.dangerReason(), "До цели дошли без опасности — причины остановки нет");
        assertEquals(entry, plan.steps().get(0).stand(), "Шаг 0 начинается со ступени входа: дух стоит там, где был");
        for (int index = 1; index < plan.steps().size(); index++) {
            SafeMineStep previous = plan.steps().get(index - 1);
            SafeMineStep step = plan.steps().get(index);
            assertEquals(
                    new BlockCoord(
                            previous.stand().x() + EAST.dx,
                            previous.stand().y() - 1,
                            previous.stand().z() + EAST.dz),
                    step.stand(),
                    "Ступени обязаны быть непрерывны: следующий шаг — на этаж ниже и на блок ближе, без разрывов");
        }
    }

    @Test
    void staircase_TorchInterval_PlacesTorchEveryIntervalSteps() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeStaircase(entry, 1);

        SafeMinePlan plan = SafeMinePlan.staircase(world, entry, EAST, 1, 4);

        for (SafeMineStep step : plan.steps()) {
            assertEquals(
                    step.index() % 4 == 0,
                    step.torch(),
                    "Факелы обязаны идти с шагом интервала из данных: иначе шахта либо темна, либо разорена");
        }
        long torches = plan.steps().stream().filter(SafeMineStep::torch).count();
        assertEquals(3, torches, "На девяти шагах с интервалом 4 обязано быть три факела (шаги 0, 4, 8)");
    }

    @Test
    void tunnel_SafeTunnel_DigsGivenLengthOnSameFloor() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeTunnel(entry, 10);

        SafeMinePlan plan = SafeMinePlan.tunnel(world, entry, EAST, 10, 4);

        assertEquals(10, plan.steps().size(), "Штольня заданной длины обязана состоять из столько же шагов");
        assertEquals(SafeMineMode.TUNNEL, plan.mode(), "Форма обязана сохраниться: штольня не превращается в спуск");
        for (SafeMineStep step : plan.steps()) {
            assertEquals(entry.y(), step.stand().y(), "Штольня обязана держать один этаж: пол не меняется");
            assertEquals(entry.z(), step.stand().z(), "Штольня обязана держать координату Z");
        }
        assertEquals(new BlockCoord(9, 10, 0), plan.steps().get(9).stand(), "Последний шаг — на длину минус один");
    }

    @Test
    void staircase_LavaBesidePassage_PlansFillInsteadOfStop() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeStaircase(entry, 1);
        BlockCoord side = new BlockCoord(2, 8, 1);
        world.set(side.x(), side.y(), side.z(), Cell.LAVA);

        SafeMinePlan plan = SafeMinePlan.staircase(world, entry, EAST, 1, 4);

        assertEquals(
                DescentStop.TARGET_Y,
                plan.stop(),
                "Лава сбоку — не повод бросать шахту: её закладывают блоком и идут дальше");
        assertTrue(
                plan.steps().stream().anyMatch(step -> step.fill().contains(side)),
                "Опасная клетка сбоку обязана попасть в заделки шага, иначе дух упрётся в лаву");
    }

    @Test
    void tunnel_WaterUnderFoot_PlansFillOfFloorCell() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeTunnel(entry, 4);
        BlockCoord floor = new BlockCoord(2, 9, 0);
        world.set(floor.x(), floor.y(), floor.z(), Cell.WATER);

        SafeMinePlan plan = SafeMinePlan.tunnel(world, entry, EAST, 4, 4);

        assertEquals(DescentStop.TARGET_Y, plan.stop(), "Вода под ногами закладывается, а не останавливает штольню");
        SafeMineStep step = plan.steps().get(2);
        assertTrue(step.fill().contains(floor), "Клетка пола с водой обязана попасть в заделки шага");
    }

    @Test
    void staircase_PitUnderFoot_StopsBeforeStepping() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeStaircase(entry, 1);
        world.set(2, 7, 0, Cell.PASSABLE);

        SafeMinePlan plan = SafeMinePlan.staircase(world, entry, EAST, 1, 4);

        assertEquals(2, plan.steps().size(), "Провал под ногами обязан обрезать план перед шагом: дух туда не шагает");
        assertEquals(DescentStop.DANGER, plan.stop(), "Провал — остановка по опасности, а не цель");
        assertTrue(
                plan.dangerReason().contains("пол"),
                "Причина обязана называть пол, иначе игрок не поймёт, куда дух не шагнул, фактически: "
                        + plan.dangerReason());
    }

    @Test
    void staircase_FluidInPassage_StopsBeforeDigging() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeStaircase(entry, 1);
        world.set(1, 9, 0, Cell.LAVA);

        SafeMinePlan plan = SafeMinePlan.staircase(world, entry, EAST, 1, 4);

        assertEquals(1, plan.steps().size(), "Жидкость в проходе останавливает проходку перед этим шагом");
        assertEquals(DescentStop.DANGER, plan.stop(), "Жидкость в проходе — опасность, а не заделка");
        assertTrue(plan.dangerReason().contains("лава"), "Причина обязана называть блок");
    }

    @Test
    void staircase_GravelAboveHead_MarksFallingWithoutStopping() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeStaircase(entry, 1);
        world.set(3, 10, 0, Cell.FALLING);

        SafeMinePlan plan = SafeMinePlan.staircase(world, entry, EAST, 1, 4);

        assertEquals(DescentStop.TARGET_Y, plan.stop(), "Сыпучий блок над головой не останавливает шахту");
        assertTrue(
                plan.steps().get(3).fallingAbove(),
                "Сыпучий блок над проходом обязан пометить шаг: сначала ждём падения, потом копаем");
        assertFalse(plan.steps().get(2).fallingAbove(), "Чужой шаг не обязан наследовать пометку соседа");
    }

    @Test
    void staircase_FallingFloor_IsAcceptedLikeSolid() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeStaircase(entry, 1);
        world.set(0, 9, 0, Cell.FALLING);

        SafeMinePlan plan = SafeMinePlan.staircase(world, entry, EAST, 1, 4);

        assertEquals(DescentStop.TARGET_Y, plan.stop(), "Сыпучий пол держит ноги так же, как твёрдый");
    }

    @Test
    void staircase_SafeShaft_ReadsElevenCellsPerStepAndNoMore() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeStaircase(entry, 1);

        SafeMinePlan.staircase(world, entry, EAST, 1, 4);

        assertEquals(
                99,
                world.reads(),
                "Каждый шаг лесенки обязан читать ровно 11 клеток (пол, три клетки прохода, шесть боковых, "
                        + "потолок): лишние чтения кладут серверный тик при планировании");
    }

    @Test
    void tunnel_LengthLessThanOne_Throws() {
        CellWorld world = new CellWorld();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SafeMinePlan.tunnel(world, new BlockCoord(0, 10, 0), EAST, 0, 4),
                "Штольня нулевой длины обязана отклоняться до чтения мира");

        assertTrue(error.getMessage().contains("Длина штольни"), "Отказ обязан называть длину штольни");
    }

    @Test
    void tunnel_TorchIntervalOutOfRange_Throws() {
        CellWorld world = new CellWorld();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SafeMinePlan.tunnel(world, new BlockCoord(0, 10, 0), EAST, 4, 0),
                "Интервал факелов 0 обязан отклоняться до чтения мира");

        assertTrue(error.getMessage().contains("Интервал факелов"), "Отказ обязан называть интервал факелов");
    }

    @Test
    void staircase_TargetYAboveWorldCeiling_Throws() {
        CellWorld world = new CellWorld();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SafeMinePlan.staircase(world, new BlockCoord(0, 10, 0), EAST, 320, 4),
                "Цель выше потолка мира обязана отклоняться до чтения мира");

        assertTrue(error.getMessage().contains("Целевой этаж"), "Отказ обязан называть целевой этаж");
    }

    @Test
    void worldBounds_ComparedToDescentPlan_AreEqual() {
        assertEquals(
                DescentPlan.WORLD_MIN_Y,
                SafeMinePlan.WORLD_MIN_Y,
                "Граница мира в плане шахты обязана совпадать с планом спуска: расхождение пустит шахту за дно");
        assertEquals(
                DescentPlan.WORLD_MAX_Y,
                SafeMinePlan.WORLD_MAX_Y,
                "Граница мира в плане шахты обязана совпадать с планом спуска: расхождение пустит шахту выше потолка");
    }
}
