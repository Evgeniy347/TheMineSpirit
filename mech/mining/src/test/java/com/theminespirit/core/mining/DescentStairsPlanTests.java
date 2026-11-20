package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DescentStairsPlanTests {
    private static final BlockCoord ENTRY = new BlockCoord(0, 4, 0);
    private static final DescentDirection EAST = DescentDirection.EAST;
    private static final int WIDTH = DescentLevels.defaults().widenWidth();
    private static final int STAIR_WIDTH = DescentLevels.defaults().stairWidth();

    @Test
    void from_SafeShaft_PlacesStairsAndLeavesRailLaneFree() {
        CellWorld world = DescentWidenPlanTests.widenWorld(0, 0, EAST, 3, 1, WIDTH);

        DescentStairsPlan plan = DescentStairsPlan.from(world, level1(world), WIDTH, STAIR_WIDTH);

        assertEquals(
                3,
                plan.steps().size(),
                "Ступеньки обязаны пройти все шаги уровня 1: по всей длине существующего спуска"
                        + " Иначе тест from_SafeShaft_PlacesStairsAndLeavesRailLaneFree перестанет ловить эту поломку. При смене from_SafeShaft_PlacesStairsAndLeavesRailLaneFree проверь это утверждение.");
        assertEquals(
                DescentStop.TARGET_Y,
                plan.stop(),
                "Безопасные ступеньки заканчиваются достижением цели"
                        + " Иначе тест from_SafeShaft_PlacesStairsAndLeavesRailLaneFree перестанет ловить эту поломку. При смене from_SafeShaft_PlacesStairsAndLeavesRailLaneFree проверь это утверждение.");
        for (DescentStairStep step : plan.steps()) {
            assertEquals(
                    STAIR_WIDTH,
                    step.stairCells().size(),
                    "Ступеньки обязаны стоять на " + STAIR_WIDTH + " полосах ширины"
                            + " Иначе тест from_SafeShaft_PlacesStairsAndLeavesRailLaneFree перестанет ловить эту поломку. При смене from_SafeShaft_PlacesStairsAndLeavesRailLaneFree проверь это утверждение.");
            assertEquals(
                    WIDTH - STAIR_WIDTH,
                    step.railCells().size(),
                    "Третья полоса обязана остаться свободной под будущие рельсы (уровень 4)"
                            + " Иначе тест from_SafeShaft_PlacesStairsAndLeavesRailLaneFree перестанет ловить эту поломку. При смене from_SafeShaft_PlacesStairsAndLeavesRailLaneFree проверь это утверждение.");
            Set<BlockCoord> all = new HashSet<>(step.stairCells());
            all.addAll(step.railCells());
            assertEquals(
                    WIDTH,
                    all.size(),
                    "Ступеньки и рельсовый проход обязаны вместе покрывать все полосы ширины без пересечений"
                            + " Иначе тест from_SafeShaft_PlacesStairsAndLeavesRailLaneFree перестанет ловить эту поломку. При смене from_SafeShaft_PlacesStairsAndLeavesRailLaneFree проверь это утверждение.");
            for (BlockCoord cell : all) {
                assertEquals(
                        step.floor() + 1,
                        cell.y(),
                        "Ступенька ставится в нижнюю клетку прохода, на пол: " + cell
                                + " Иначе тест from_SafeShaft_PlacesStairsAndLeavesRailLaneFree перестанет ловить эту поломку. При смене from_SafeShaft_PlacesStairsAndLeavesRailLaneFree проверь это утверждение.");
            }
            for (BlockCoord stair : step.stairCells()) {
                assertFalse(
                        step.railCells().contains(stair),
                        "Полоса рельсов не может быть ступенькой: рельсы прокладывают по свободной полосе"
                                + " Иначе тест from_SafeShaft_PlacesStairsAndLeavesRailLaneFree перестанет ловить эту поломку. При смене from_SafeShaft_PlacesStairsAndLeavesRailLaneFree проверь это утверждение.");
            }
        }
    }

    @Test
    void from_SafeShaft_RailLaneIsCenterColumn() {
        CellWorld world = DescentWidenPlanTests.widenWorld(0, 0, EAST, 3, 1, WIDTH);

        DescentStairsPlan plan = DescentStairsPlan.from(world, level1(world), WIDTH, STAIR_WIDTH);

        DescentStairStep first = plan.steps().get(0);
        BlockCoord rail = first.railCells().get(0);
        assertEquals(
                ENTRY.x(),
                rail.x(),
                "Рельсовая полоса совпадает с центральной колонкой спуска по X"
                        + " Иначе тест from_SafeShaft_RailLaneIsCenterColumn перестанет ловить эту поломку. При смене from_SafeShaft_RailLaneIsCenterColumn проверь это утверждение.");
        assertEquals(
                ENTRY.z(),
                rail.z(),
                "Рельсовая полоса совпадает с центральной колонкой спуска по Z"
                        + " Иначе тест from_SafeShaft_RailLaneIsCenterColumn перестанет ловить эту поломку. При смене from_SafeShaft_RailLaneIsCenterColumn проверь это утверждение.");
        assertEquals(
                first.floor() + 1,
                rail.y(),
                "Рельсовый проход начинается с пола: высота пола плюс один"
                        + " Иначе тест from_SafeShaft_RailLaneIsCenterColumn перестанет ловить эту поломку. При смене from_SafeShaft_RailLaneIsCenterColumn проверь это утверждение.");
    }

    @Test
    void from_LavaInStairCellOfLaterStep_StopsBeforeIt() {
        CellWorld world =
                DescentWidenPlanTests.widenWorld(0, 0, EAST, 3, 1, WIDTH).set(1, 3, 1, Cell.LAVA);

        DescentStairsPlan plan = DescentStairsPlan.from(world, level1(world), WIDTH, STAIR_WIDTH);

        assertEquals(
                1,
                plan.steps().size(),
                "Перед лавой в клетке ступеньки план ступенек обязан обрезаться: шаг с лавой не выполняется"
                        + " Иначе тест from_LavaInStairCellOfLaterStep_StopsBeforeIt перестанет ловить эту поломку. При смене from_LavaInStairCellOfLaterStep_StopsBeforeIt проверь это утверждение.");
        assertEquals(
                DescentStop.DANGER,
                plan.stop(),
                "Лава — остановка по опасности, а не достижение цели"
                        + " Иначе тест from_LavaInStairCellOfLaterStep_StopsBeforeIt перестанет ловить эту поломку. При смене from_LavaInStairCellOfLaterStep_StopsBeforeIt проверь это утверждение.");
        assertTrue(
                plan.dangerReason().startsWith("опасность: "),
                "Причина обязана начинаться с 'опасность: ': по формату её узнают отчёт и игрок"
                        + " Иначе тест from_LavaInStairCellOfLaterStep_StopsBeforeIt перестанет ловить эту поломку. При смене from_LavaInStairCellOfLaterStep_StopsBeforeIt проверь это утверждение.");
        assertTrue(
                plan.dangerReason().contains("лава"),
                "Причина обязана называть лаву"
                        + " Иначе тест from_LavaInStairCellOfLaterStep_StopsBeforeIt перестанет ловить эту поломку. При смене from_LavaInStairCellOfLaterStep_StopsBeforeIt проверь это утверждение.");
        assertTrue(
                plan.dangerReason().contains("(1, 3, 1)"),
                "Причина обязана называть клетку с лавой"
                        + " Иначе тест from_LavaInStairCellOfLaterStep_StopsBeforeIt перестанет ловить эту поломку. При смене from_LavaInStairCellOfLaterStep_StopsBeforeIt проверь это утверждение.");
    }

    @Test
    void from_VoidBelowStair_StopsWithVoidReason() {
        CellWorld world =
                DescentWidenPlanTests.widenWorld(0, 0, EAST, 3, 1, WIDTH).set(0, 3, 1, Cell.PASSABLE);

        DescentStairsPlan plan = DescentStairsPlan.from(world, level1(world), WIDTH, STAIR_WIDTH);

        assertTrue(
                plan.steps().isEmpty(),
                "Нетвёрдый пол под ступенькой первого шага обязан остановить план до укладки"
                        + " Иначе тест from_VoidBelowStair_StopsWithVoidReason перестанет ловить эту поломку. При смене from_VoidBelowStair_StopsWithVoidReason проверь это утверждение.");
        assertEquals(
                DescentStop.DANGER,
                plan.stop(),
                "Пустота вместо пола — остановка по опасности"
                        + " Иначе тест from_VoidBelowStair_StopsWithVoidReason перестанет ловить эту поломку. При смене from_VoidBelowStair_StopsWithVoidReason проверь это утверждение.");
        assertTrue(
                plan.dangerReason().contains("пустота"),
                "Причина обязана называть пустоту"
                        + " Иначе тест from_VoidBelowStair_StopsWithVoidReason перестанет ловить эту поломку. При смене from_VoidBelowStair_StopsWithVoidReason проверь это утверждение.");
    }

    @Test
    void from_StairWidthNotLessThanWidenWidth_Throws() {
        CellWorld world = DescentWidenPlanTests.widenWorld(0, 0, EAST, 3, 1, WIDTH);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DescentStairsPlan.from(world, level1(world), WIDTH, WIDTH),
                "Ступеньки на всех полосах обязаны отклоняться: третью оставляют под рельсы"
                        + " Иначе тест from_StairWidthNotLessThanWidenWidth_Throws перестанет ловить эту поломку. При смене from_StairWidthNotLessThanWidenWidth_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Ширина ступенек"),
                "Отказ обязан называть ширину ступенек"
                        + " Иначе тест from_StairWidthNotLessThanWidenWidth_Throws перестанет ловить эту поломку. При смене from_StairWidthNotLessThanWidenWidth_Throws проверь это утверждение.");
    }

    @Test
    void from_EvenWidenWidth_Throws() {
        CellWorld world = DescentWidenPlanTests.widenWorld(0, 0, EAST, 3, 1, WIDTH);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DescentStairsPlan.from(world, level1(world), 4, 2),
                "Чётная ширина расширения обязана отклоняться: проход симметричен центральной колонке"
                        + " Иначе тест from_EvenWidenWidth_Throws перестанет ловить эту поломку. При смене from_EvenWidenWidth_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Ширина расширения"),
                "Отказ обязан называть ширину расширения"
                        + " Иначе тест from_EvenWidenWidth_Throws перестанет ловить эту поломку. При смене from_EvenWidenWidth_Throws проверь это утверждение.");
    }

    private static DescentPlan level1(CellWorld world) {
        return DescentPlan.from(world, ENTRY, EAST, 1, 4);
    }
}
