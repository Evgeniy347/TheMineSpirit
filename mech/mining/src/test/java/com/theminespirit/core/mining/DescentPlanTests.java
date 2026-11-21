package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DescentPlanTests {
    static final BlockCoord ENTRY = new BlockCoord(0, 4, 0);
    static final DescentDirection EAST = DescentDirection.EAST;

    static Stream<Arguments> invalidFloorCells() {
        return Stream.of(
                Arguments.of(Cell.PASSABLE, "пустота"),
                Arguments.of(Cell.WATER, "вода"),
                Arguments.of(Cell.LAVA, "лава"),
                Arguments.of(Cell.FIRE, "огонь"),
                Arguments.of(Cell.HAZARD, "опасный блок"),
                Arguments.of(Cell.PORTAL, "портал"));
    }

    @Test
    void from_SafeShaft_BuildsStepsDownToTargetY() {
        CellWorld world = solidDescent(new CellWorld(), 0, 0, EAST, 3, 1);

        DescentPlan plan = DescentPlan.from(world, ENTRY, EAST, 1, 4);

        assertEquals(
                3,
                plan.steps().size(),
                "Спуск с этажа 3 до целевого 1 обязан состоять из трёх шагов: по одному на каждый этаж"
                        + " Иначе тест from_SafeShaft_BuildsStepsDownToTargetY перестанет ловить эту поломку. При смене from_SafeShaft_BuildsStepsDownToTargetY проверь это утверждение.");
        assertEquals(
                DescentStop.TARGET_Y,
                plan.stop(),
                "Безопасная шахта без опасных клеток должна заканчиваться достижением цели, а не паникой"
                        + " Иначе тест from_SafeShaft_BuildsStepsDownToTargetY перестанет ловить эту поломку. При смене from_SafeShaft_BuildsStepsDownToTargetY проверь это утверждение.");
        assertEquals(
                "",
                plan.dangerReason(),
                "До цели дошли без опасности — причины остановки нет"
                        + " Иначе тест from_SafeShaft_BuildsStepsDownToTargetY перестанет ловить эту поломку. При смене from_SafeShaft_BuildsStepsDownToTargetY проверь это утверждение.");
        assertEquals(
                new BlockCoord(0, 4, 0),
                plan.steps().get(0).stand(),
                "Шаг 0 начинается со ступени входа: дух стоит там, где был"
                        + " Иначе тест from_SafeShaft_BuildsStepsDownToTargetY перестанет ловить эту поломку. При смене from_SafeShaft_BuildsStepsDownToTargetY проверь это утверждение.");
        assertEquals(
                List.of(new BlockCoord(0, 4, 0), new BlockCoord(0, 5, 0), new BlockCoord(0, 6, 0)),
                plan.steps().get(0).passage(),
                "Проход шага 0 — собственная колонка входа на высотах floor+1..floor+3"
                        + " Иначе тест from_SafeShaft_BuildsStepsDownToTargetY перестанет ловить эту поломку. При смене from_SafeShaft_BuildsStepsDownToTargetY проверь это утверждение.");
        assertEquals(
                new BlockCoord(1, 3, 0),
                plan.steps().get(1).stand(),
                "Шаг 1 — лесенкой: на этаж ниже и на блок ближе по направлению спуска"
                        + " Иначе тест from_SafeShaft_BuildsStepsDownToTargetY перестанет ловить эту поломку. При смене from_SafeShaft_BuildsStepsDownToTargetY проверь это утверждение.");
        assertEquals(
                List.of(new BlockCoord(1, 3, 0), new BlockCoord(1, 4, 0), new BlockCoord(1, 5, 0)),
                plan.steps().get(1).passage(),
                "Проход шага 1 — колонка его ступени на три клетки вверх"
                        + " Иначе тест from_SafeShaft_BuildsStepsDownToTargetY перестанет ловить эту поломку. При смене from_SafeShaft_BuildsStepsDownToTargetY проверь это утверждение.");
        assertEquals(
                new BlockCoord(2, 2, 0),
                plan.steps().get(2).stand(),
                "Последний шаг встаёт на этаж targetY в своей колонке"
                        + " Иначе тест from_SafeShaft_BuildsStepsDownToTargetY перестанет ловить эту поломку. При смене from_SafeShaft_BuildsStepsDownToTargetY проверь это утверждение.");
    }

    @Test
    void from_StepTorch_LivesInStandCell() {
        CellWorld world = solidDescent(new CellWorld(), 0, 0, EAST, 3, 1);

        DescentPlan plan = DescentPlan.from(world, ENTRY, EAST, 1, 4);

        for (DescentStep step : plan.steps()) {
            assertEquals(
                    step.stand(),
                    step.torch(),
                    "Факел ставится в клетку ступней: освещает и площадку, и следующий выступ"
                            + " Иначе тест from_StepTorch_LivesInStandCell перестанет ловить эту поломку. При смене from_StepTorch_LivesInStandCell проверь это утверждение.");
        }
    }

    @Test
    void from_EntryOnTargetFloor_YieldsEmptyPlanToTargetY() {
        CellWorld world = new CellWorld();

        DescentPlan plan = DescentPlan.from(world, new BlockCoord(0, 2, 0), EAST, 2, 4);

        assertTrue(
                plan.steps().isEmpty(),
                "Дух уже на целевом этаже — копать нечего"
                        + " Иначе тест from_EntryOnTargetFloor_YieldsEmptyPlanToTargetY перестанет ловить эту поломку. При смене from_EntryOnTargetFloor_YieldsEmptyPlanToTargetY проверь это утверждение.");
        assertEquals(
                DescentStop.TARGET_Y,
                plan.stop(),
                "Достигнутая цель — не опасность"
                        + " Иначе тест from_EntryOnTargetFloor_YieldsEmptyPlanToTargetY перестанет ловить эту поломку. При смене from_EntryOnTargetFloor_YieldsEmptyPlanToTargetY проверь это утверждение.");
    }

    @Test
    void from_EntryBelowTargetFloor_YieldsEmptyPlanToTargetY() {
        CellWorld world = new CellWorld();

        DescentPlan plan = DescentPlan.from(world, new BlockCoord(0, 1, 0), EAST, 5, 4);

        assertTrue(
                plan.steps().isEmpty(),
                "Цель выше текущего этажа духа — спускаться не нужно"
                        + " Иначе тест from_EntryBelowTargetFloor_YieldsEmptyPlanToTargetY перестанет ловить эту поломку. При смене from_EntryBelowTargetFloor_YieldsEmptyPlanToTargetY проверь это утверждение.");
        assertEquals(
                DescentStop.TARGET_Y,
                plan.stop(),
                "Достигнутая цель — не опасность"
                        + " Иначе тест from_EntryBelowTargetFloor_YieldsEmptyPlanToTargetY перестанет ловить эту поломку. При смене from_EntryBelowTargetFloor_YieldsEmptyPlanToTargetY проверь это утверждение.");
    }

    @Test
    void from_LavaBesidePassageOfLaterStep_StopsWithLavaReasonBeforeIt() {
        CellWorld world = solidDescent(new CellWorld(), 0, 0, EAST, 3, 1).set(1, 3, -1, Cell.LAVA);

        DescentPlan plan = DescentPlan.from(world, ENTRY, EAST, 1, 4);

        assertEquals(
                1,
                plan.steps().size(),
                "Перед лавой у боковой стенки прохода план обязан обрезаться: шаг с лавой не выполняется"
                        + " Иначе тест from_LavaBesidePassageOfLaterStep_StopsWithLavaReasonBeforeIt перестанет ловить эту поломку. При смене from_LavaBesidePassageOfLaterStep_StopsWithLavaReasonBeforeIt проверь это утверждение.");
        assertEquals(
                DescentStop.DANGER,
                plan.stop(),
                "Лава — это остановка по опасности, а не достижение цели"
                        + " Иначе тест from_LavaBesidePassageOfLaterStep_StopsWithLavaReasonBeforeIt перестанет ловить эту поломку. При смене from_LavaBesidePassageOfLaterStep_StopsWithLavaReasonBeforeIt проверь это утверждение.");
        assertTrue(
                plan.dangerReason().startsWith("опасность: "),
                "Причина остановки должна начинаться с 'опасность: ': по такому формату её узнают отчёт и игрок"
                        + " Иначе тест from_LavaBesidePassageOfLaterStep_StopsWithLavaReasonBeforeIt перестанет ловить эту поломку. При смене from_LavaBesidePassageOfLaterStep_StopsWithLavaReasonBeforeIt проверь это утверждение.");
        assertTrue(
                plan.dangerReason().contains("лава"),
                "Причина обязана называть блок: 'лава' в тексте позволяет отличить лаву от пустоты"
                        + " Иначе тест from_LavaBesidePassageOfLaterStep_StopsWithLavaReasonBeforeIt перестанет ловить эту поломку. При смене from_LavaBesidePassageOfLaterStep_StopsWithLavaReasonBeforeIt проверь это утверждение.");
        assertTrue(
                plan.dangerReason().contains("(1, 3, -1)"),
                "Причина обязана называть клетку: игрок должен увидеть, где именно опасность"
                        + " Иначе тест from_LavaBesidePassageOfLaterStep_StopsWithLavaReasonBeforeIt перестанет ловить эту поломку. При смене from_LavaBesidePassageOfLaterStep_StopsWithLavaReasonBeforeIt проверь это утверждение.");
    }

    @Test
    void from_LavaInSubfloorOfLaterStep_StopsWithLavaReasonBeforeIt() {
        CellWorld world = solidDescent(new CellWorld(), 0, 0, EAST, 3, 1).set(1, 1, 0, Cell.LAVA);

        DescentPlan plan = DescentPlan.from(world, ENTRY, EAST, 1, 4);

        assertEquals(
                1,
                plan.steps().size(),
                "Перед лавой под полом следующего шага план обязан обрезаться"
                        + " Иначе тест from_LavaInSubfloorOfLaterStep_StopsWithLavaReasonBeforeIt перестанет ловить эту поломку. При смене from_LavaInSubfloorOfLaterStep_StopsWithLavaReasonBeforeIt проверь это утверждение.");
        assertEquals(
                DescentStop.DANGER,
                plan.stop(),
                "Лава вместо пола — остановка по опасности"
                        + " Иначе тест from_LavaInSubfloorOfLaterStep_StopsWithLavaReasonBeforeIt перестанет ловить эту поломку. При смене from_LavaInSubfloorOfLaterStep_StopsWithLavaReasonBeforeIt проверь это утверждение.");
        assertTrue(
                plan.dangerReason().contains("лава"),
                "Причина обязана называть блок"
                        + " Иначе тест from_LavaInSubfloorOfLaterStep_StopsWithLavaReasonBeforeIt перестанет ловить эту поломку. При смене from_LavaInSubfloorOfLaterStep_StopsWithLavaReasonBeforeIt проверь это утверждение.");
        assertTrue(
                plan.dangerReason().contains("подпол"),
                "Причина обязана называть роль клетки: пол или подпол"
                        + " Иначе тест from_LavaInSubfloorOfLaterStep_StopsWithLavaReasonBeforeIt перестанет ловить эту поломку. При смене from_LavaInSubfloorOfLaterStep_StopsWithLavaReasonBeforeIt проверь это утверждение.");
    }

    @Test
    void from_WaterInPassageOfFirstStep_StopsWithWaterReason() {
        CellWorld world = solidDescent(new CellWorld(), 0, 0, EAST, 3, 1).set(0, 4, 0, Cell.WATER);

        DescentPlan plan = DescentPlan.from(world, ENTRY, EAST, 1, 4);

        assertTrue(
                plan.steps().isEmpty(),
                "Вода в проходе первого шага останавливает спуск до первого шага"
                        + " Иначе тест from_WaterInPassageOfFirstStep_StopsWithWaterReason перестанет ловить эту поломку. При смене from_WaterInPassageOfFirstStep_StopsWithWaterReason проверь это утверждение.");
        assertEquals(
                DescentStop.DANGER,
                plan.stop(),
                "Вода в проходе — остановка по опасности"
                        + " Иначе тест from_WaterInPassageOfFirstStep_StopsWithWaterReason перестанет ловить эту поломку. При смене from_WaterInPassageOfFirstStep_StopsWithWaterReason проверь это утверждение.");
        assertTrue(
                plan.dangerReason().contains("вода"),
                "Причина обязана называть блок"
                        + " Иначе тест from_WaterInPassageOfFirstStep_StopsWithWaterReason перестанет ловить эту поломку. При смене from_WaterInPassageOfFirstStep_StopsWithWaterReason проверь это утверждение.");
    }

    @ParameterizedTest(name = "пол из {0} — {1}")
    @MethodSource("invalidFloorCells")
    void from_NonSolidFloorCell_StopsWithMatchingLabel(Cell cell, String label) {
        CellWorld world = solidDescent(new CellWorld(), 0, 0, EAST, 3, 1).set(0, 3, 0, cell);

        DescentPlan plan = DescentPlan.from(world, ENTRY, EAST, 1, 4);

        assertTrue(
                plan.steps().isEmpty(),
                "Нетвёрдый пол первого шага обязан останавливать спуск до копания"
                        + " Иначе тест from_NonSolidFloorCell_StopsWithMatchingLabel перестанет ловить эту поломку. При смене from_NonSolidFloorCell_StopsWithMatchingLabel проверь это утверждение.");
        assertEquals(
                DescentStop.DANGER,
                plan.stop(),
                "Нетвёрдый пол — остановка по опасности"
                        + " Иначе тест from_NonSolidFloorCell_StopsWithMatchingLabel перестанет ловить эту поломку. При смене from_NonSolidFloorCell_StopsWithMatchingLabel проверь это утверждение.");
        assertTrue(
                plan.dangerReason().contains(label),
                "Причина обязана называть блок словом '" + label + "', иначе игрок не отличит опасность"
                        + " При смене from_NonSolidFloorCell_StopsWithMatchingLabel проверь это утверждение.");
    }

    @Test
    void from_FallingFloor_IsAcceptedAsSolid() {
        CellWorld world = solidDescent(new CellWorld(), 0, 0, EAST, 3, 1).set(0, 3, 0, Cell.FALLING);

        DescentPlan plan = DescentPlan.from(world, ENTRY, EAST, 1, 4);

        assertEquals(
                3,
                plan.steps().size(),
                "Сыпучий пол (песок, гравий) держит ноги духа: план обязан строиться как для твёрдого"
                        + " Иначе тест from_FallingFloor_IsAcceptedAsSolid перестанет ловить эту поломку. При смене from_FallingFloor_IsAcceptedAsSolid проверь это утверждение.");
        assertEquals(
                DescentStop.TARGET_Y,
                plan.stop(),
                "Сыпучий пол — не опасность"
                        + " Иначе тест from_FallingFloor_IsAcceptedAsSolid перестанет ловить эту поломку. При смене from_FallingFloor_IsAcceptedAsSolid проверь это утверждение.");
    }

    @Test
    void from_EmptySideWall_DoesNotStopPlan() {
        CellWorld world = solidDescent(new CellWorld(), 0, 0, EAST, 3, 1);

        DescentPlan plan = DescentPlan.from(world, ENTRY, EAST, 1, 4);

        assertEquals(
                DescentStop.TARGET_Y,
                plan.stop(),
                "Пустая боковая стенка не льёт на духа жидкость и не мешает проходу: останавливаться нельзя"
                        + " Иначе тест from_EmptySideWall_DoesNotStopPlan перестанет ловить эту поломку. При смене from_EmptySideWall_DoesNotStopPlan проверь это утверждение.");
    }

    @Test
    void from_SafeShaft_ReadsExactlyElevenCellsPerStep() {
        CellWorld world = solidDescent(new CellWorld(), 0, 0, EAST, 3, 1);

        DescentPlan.from(world, ENTRY, EAST, 1, 4);

        assertEquals(
                33,
                world.reads(),
                "Каждый шаг обязан читать ровно 11 клеток (пол, подпол, 3 клетки прохода с двумя боковыми): "
                        + "лишние чтения кладут серверный тик при планировании"
                        + " Иначе тест from_SafeShaft_ReadsExactlyElevenCellsPerStep перестанет ловить эту поломку. При смене from_SafeShaft_ReadsExactlyElevenCellsPerStep проверь это утверждение.");
    }

    static CellWorld solidDescent(
            CellWorld world, int x, int z, DescentDirection direction, int entryFloor, int targetY) {
        int index = 0;
        for (int floor = entryFloor; floor >= targetY; floor--, index++) {
            int columnX = x + index * direction.dx;
            int columnZ = z + index * direction.dz;
            world.set(columnX, floor, columnZ, Cell.SOLID);
            world.set(columnX, floor - 1, columnZ, Cell.SOLID);
            for (int height = floor + 1; height <= floor + 3; height++) {
                world.set(columnX, height, columnZ, Cell.SOLID);
            }
        }
        return world;
    }
}
