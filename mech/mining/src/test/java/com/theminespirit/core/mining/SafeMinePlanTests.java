package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class SafeMinePlanTests {
    static final DescentDirection EAST = DescentDirection.EAST;

    static CellWorld safeStaircase(BlockCoord entry, int targetY) {
        CellWorld world = new CellWorld();
        int entryFloor = entry.y() - 1;
        for (int floor = entryFloor; floor >= targetY; floor--) {
            world.set(entry.x() + (entryFloor - floor), floor, entry.z(), Cell.SOLID);
        }
        return world;
    }

    static CellWorld safeTunnel(BlockCoord entry, int length) {
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
                "Спуск с этажа 9 до целевого 1 обязан состоять из девяти шагов: по одному на каждый этаж"
                        + " Иначе тест staircase_SafeShaft_StepsAreContinuousDownAndForward перестанет ловить эту поломку. При смене staircase_SafeShaft_StepsAreContinuousDownAndForward проверь это утверждение.");
        assertEquals(
                DescentStop.TARGET_Y,
                plan.stop(),
                "Безопасная шахта без опасных клеток заканчивается целью"
                        + " Иначе тест staircase_SafeShaft_StepsAreContinuousDownAndForward перестанет ловить эту поломку. При смене staircase_SafeShaft_StepsAreContinuousDownAndForward проверь это утверждение.");
        assertEquals(
                "",
                plan.dangerReason(),
                "До цели дошли без опасности — причины остановки нет"
                        + " Иначе тест staircase_SafeShaft_StepsAreContinuousDownAndForward перестанет ловить эту поломку. При смене staircase_SafeShaft_StepsAreContinuousDownAndForward проверь это утверждение.");
        assertEquals(
                entry,
                plan.steps().get(0).stand(),
                "Шаг 0 начинается со ступени входа: дух стоит там, где был"
                        + " Иначе тест staircase_SafeShaft_StepsAreContinuousDownAndForward перестанет ловить эту поломку. При смене staircase_SafeShaft_StepsAreContinuousDownAndForward проверь это утверждение.");
        for (int index = 1; index < plan.steps().size(); index++) {
            SafeMineStep previous = plan.steps().get(index - 1);
            SafeMineStep step = plan.steps().get(index);
            assertEquals(
                    new BlockCoord(
                            previous.stand().x() + EAST.dx,
                            previous.stand().y() - 1,
                            previous.stand().z() + EAST.dz),
                    step.stand(),
                    "Ступени обязаны быть непрерывны: следующий шаг — на этаж ниже и на блок ближе, без разрывов"
                            + " Иначе тест staircase_SafeShaft_StepsAreContinuousDownAndForward перестанет ловить эту поломку. При смене staircase_SafeShaft_StepsAreContinuousDownAndForward проверь это утверждение.");
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
                    "Факелы обязаны идти с шагом интервала из данных: иначе шахта либо темна, либо разорена"
                            + " При смене staircase_TorchInterval_PlacesTorchEveryIntervalSteps проверь это утверждение.");
        }
        long torches = plan.steps().stream().filter(SafeMineStep::torch).count();
        assertEquals(
                3,
                torches,
                "На девяти шагах с интервалом 4 обязано быть три факела (шаги 0, 4, 8)"
                        + " Иначе тест staircase_TorchInterval_PlacesTorchEveryIntervalSteps перестанет ловить эту поломку. При смене staircase_TorchInterval_PlacesTorchEveryIntervalSteps проверь это утверждение.");
    }

    @Test
    void tunnel_SafeTunnel_DigsGivenLengthOnSameFloor() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeTunnel(entry, 10);

        SafeMinePlan plan = SafeMinePlan.tunnel(world, entry, EAST, 10, 4);

        assertEquals(
                10,
                plan.steps().size(),
                "Штольня заданной длины обязана состоять из столько же шагов"
                        + " Иначе тест tunnel_SafeTunnel_DigsGivenLengthOnSameFloor перестанет ловить эту поломку. При смене tunnel_SafeTunnel_DigsGivenLengthOnSameFloor проверь это утверждение.");
        assertEquals(
                SafeMineMode.TUNNEL,
                plan.mode(),
                "Форма обязана сохраниться: штольня не превращается в спуск"
                        + " Иначе тест tunnel_SafeTunnel_DigsGivenLengthOnSameFloor перестанет ловить эту поломку. При смене tunnel_SafeTunnel_DigsGivenLengthOnSameFloor проверь это утверждение.");
        for (SafeMineStep step : plan.steps()) {
            assertEquals(
                    entry.y(),
                    step.stand().y(),
                    "Штольня обязана держать один этаж: пол не меняется"
                            + " Иначе тест tunnel_SafeTunnel_DigsGivenLengthOnSameFloor перестанет ловить эту поломку. При смене tunnel_SafeTunnel_DigsGivenLengthOnSameFloor проверь это утверждение.");
            assertEquals(
                    entry.z(),
                    step.stand().z(),
                    "Штольня обязана держать координату Z"
                            + " Иначе тест tunnel_SafeTunnel_DigsGivenLengthOnSameFloor перестанет ловить эту поломку. При смене tunnel_SafeTunnel_DigsGivenLengthOnSameFloor проверь это утверждение.");
        }
        assertEquals(
                new BlockCoord(9, 10, 0),
                plan.steps().get(9).stand(),
                "Последний шаг — на длину минус один"
                        + " Иначе тест tunnel_SafeTunnel_DigsGivenLengthOnSameFloor перестанет ловить эту поломку. При смене tunnel_SafeTunnel_DigsGivenLengthOnSameFloor проверь это утверждение.");
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
                "Лава сбоку — не повод бросать шахту: её закладывают блоком и идут дальше"
                        + " Иначе тест staircase_LavaBesidePassage_PlansFillInsteadOfStop перестанет ловить эту поломку. При смене staircase_LavaBesidePassage_PlansFillInsteadOfStop проверь это утверждение.");
        assertTrue(
                plan.steps().stream().anyMatch(step -> step.fill().contains(side)),
                "Опасная клетка сбоку обязана попасть в заделки шага, иначе дух упрётся в лаву"
                        + " При смене staircase_LavaBesidePassage_PlansFillInsteadOfStop проверь это утверждение.");
    }

    @Test
    void tunnel_WaterUnderFoot_PlansFillOfFloorCell() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeTunnel(entry, 4);
        BlockCoord floor = new BlockCoord(2, 9, 0);
        world.set(floor.x(), floor.y(), floor.z(), Cell.WATER);

        SafeMinePlan plan = SafeMinePlan.tunnel(world, entry, EAST, 4, 4);

        assertEquals(
                DescentStop.TARGET_Y,
                plan.stop(),
                "Вода под ногами закладывается, а не останавливает штольню"
                        + " Иначе тест tunnel_WaterUnderFoot_PlansFillOfFloorCell перестанет ловить эту поломку. При смене tunnel_WaterUnderFoot_PlansFillOfFloorCell проверь это утверждение.");
        SafeMineStep step = plan.steps().get(2);
        assertTrue(
                step.fill().contains(floor),
                "Клетка пола с водой обязана попасть в заделки шага"
                        + " Иначе тест tunnel_WaterUnderFoot_PlansFillOfFloorCell перестанет ловить эту поломку. При смене tunnel_WaterUnderFoot_PlansFillOfFloorCell проверь это утверждение.");
    }

    @Test
    void staircase_PitUnderFoot_StopsBeforeStepping() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeStaircase(entry, 1);
        world.set(2, 7, 0, Cell.PASSABLE);

        SafeMinePlan plan = SafeMinePlan.staircase(world, entry, EAST, 1, 4);

        assertEquals(
                2,
                plan.steps().size(),
                "Провал под ногами обязан обрезать план перед шагом: дух туда не шагает"
                        + " Иначе тест staircase_PitUnderFoot_StopsBeforeStepping перестанет ловить эту поломку. При смене staircase_PitUnderFoot_StopsBeforeStepping проверь это утверждение.");
        assertEquals(
                DescentStop.DANGER,
                plan.stop(),
                "Провал — остановка по опасности, а не цель"
                        + " Иначе тест staircase_PitUnderFoot_StopsBeforeStepping перестанет ловить эту поломку. При смене staircase_PitUnderFoot_StopsBeforeStepping проверь это утверждение.");
        assertTrue(
                plan.dangerReason().contains("пол"),
                "Причина обязана называть пол, иначе игрок не поймёт, куда дух не шагнул, фактически: "
                        + plan.dangerReason()
                        + " При смене staircase_PitUnderFoot_StopsBeforeStepping проверь это утверждение.");
    }

    @Test
    void staircase_FluidInPassage_StopsBeforeDigging() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeStaircase(entry, 1);
        world.set(1, 9, 0, Cell.LAVA);

        SafeMinePlan plan = SafeMinePlan.staircase(world, entry, EAST, 1, 4);

        assertEquals(
                1,
                plan.steps().size(),
                "Жидкость в проходе останавливает проходку перед этим шагом"
                        + " Иначе тест staircase_FluidInPassage_StopsBeforeDigging перестанет ловить эту поломку. При смене staircase_FluidInPassage_StopsBeforeDigging проверь это утверждение.");
        assertEquals(
                DescentStop.DANGER,
                plan.stop(),
                "Жидкость в проходе — опасность, а не заделка"
                        + " Иначе тест staircase_FluidInPassage_StopsBeforeDigging перестанет ловить эту поломку. При смене staircase_FluidInPassage_StopsBeforeDigging проверь это утверждение.");
        assertTrue(
                plan.dangerReason().contains("лава"),
                "Причина обязана называть блок"
                        + " Иначе тест staircase_FluidInPassage_StopsBeforeDigging перестанет ловить эту поломку. При смене staircase_FluidInPassage_StopsBeforeDigging проверь это утверждение.");
    }

    @Test
    void staircase_GravelAboveHead_MarksFallingWithoutStopping() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeStaircase(entry, 1);
        world.set(3, 10, 0, Cell.FALLING);

        SafeMinePlan plan = SafeMinePlan.staircase(world, entry, EAST, 1, 4);

        assertEquals(
                DescentStop.TARGET_Y,
                plan.stop(),
                "Сыпучий блок над головой не останавливает шахту"
                        + " Иначе тест staircase_GravelAboveHead_MarksFallingWithoutStopping перестанет ловить эту поломку. При смене staircase_GravelAboveHead_MarksFallingWithoutStopping проверь это утверждение.");
        assertTrue(
                plan.steps().get(3).fallingAbove(),
                "Сыпучий блок над проходом обязан пометить шаг: сначала ждём падения, потом копаем"
                        + " Иначе тест staircase_GravelAboveHead_MarksFallingWithoutStopping перестанет ловить эту поломку. При смене staircase_GravelAboveHead_MarksFallingWithoutStopping проверь это утверждение.");
        assertFalse(
                plan.steps().get(2).fallingAbove(),
                "Чужой шаг не обязан наследовать пометку соседа"
                        + " Иначе тест staircase_GravelAboveHead_MarksFallingWithoutStopping перестанет ловить эту поломку. При смене staircase_GravelAboveHead_MarksFallingWithoutStopping проверь это утверждение.");
    }

    @Test
    void staircase_FallingFloor_IsAcceptedLikeSolid() {
        BlockCoord entry = new BlockCoord(0, 10, 0);
        CellWorld world = safeStaircase(entry, 1);
        world.set(0, 9, 0, Cell.FALLING);

        SafeMinePlan plan = SafeMinePlan.staircase(world, entry, EAST, 1, 4);

        assertEquals(
                DescentStop.TARGET_Y,
                plan.stop(),
                "Сыпучий пол держит ноги так же, как твёрдый"
                        + " Иначе тест staircase_FallingFloor_IsAcceptedLikeSolid перестанет ловить эту поломку. При смене staircase_FallingFloor_IsAcceptedLikeSolid проверь это утверждение.");
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
                        + "потолок): лишние чтения кладут серверный тик при планировании"
                        + " Иначе тест staircase_SafeShaft_ReadsElevenCellsPerStepAndNoMore перестанет ловить эту поломку. При смене staircase_SafeShaft_ReadsElevenCellsPerStepAndNoMore проверь это утверждение.");
    }

    @Test
    void tunnel_LengthLessThanOne_Throws() {
        CellWorld world = new CellWorld();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SafeMinePlan.tunnel(world, new BlockCoord(0, 10, 0), EAST, 0, 4),
                "Штольня нулевой длины обязана отклоняться до чтения мира"
                        + " Иначе тест tunnel_LengthLessThanOne_Throws перестанет ловить эту поломку. При смене tunnel_LengthLessThanOne_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Длина штольни"),
                "Отказ обязан называть длину штольни"
                        + " Иначе тест tunnel_LengthLessThanOne_Throws перестанет ловить эту поломку. При смене tunnel_LengthLessThanOne_Throws проверь это утверждение.");
    }
}
