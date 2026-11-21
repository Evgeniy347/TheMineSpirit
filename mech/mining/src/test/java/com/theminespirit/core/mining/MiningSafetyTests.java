package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.BreakSafety;
import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.mining.api.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MiningSafetyTests {
    static Stream<Arguments> fluidFaces() {
        return Stream.of(
                Arguments.of(0, 1, 0, Cell.LAVA), Arguments.of(0, -1, 0, Cell.LAVA),
                Arguments.of(1, 0, 0, Cell.LAVA), Arguments.of(-1, 0, 0, Cell.LAVA),
                Arguments.of(0, 0, 1, Cell.LAVA), Arguments.of(0, 0, -1, Cell.LAVA),
                Arguments.of(0, 1, 0, Cell.WATER), Arguments.of(0, -1, 0, Cell.WATER),
                Arguments.of(1, 0, 0, Cell.WATER), Arguments.of(-1, 0, 0, Cell.WATER),
                Arguments.of(0, 0, 1, Cell.WATER), Arguments.of(0, 0, -1, Cell.WATER));
    }

    @ParameterizedTest(name = "грань ({0},{1},{2}), {3}")
    @MethodSource("fluidFaces")
    void canBreak_FluidOnAnyFace_IsUnsafe(int dx, int dy, int dz, Cell fluid) {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID).set(dx, dy, dz, fluid);

        BreakSafety safety = MiningSafety.canBreak(new BlockCoord(0, 0, 0), world);

        assertTrue(
                safety instanceof BreakSafety.Unsafe,
                "Жидкость на грани " + fluid + " (" + dx + "," + dy + "," + dz + ") обязана запрещать ломку: "
                        + "затекшая в полость жидкость смывает духа или убивает его лавой"
                        + " Иначе тест canBreak_FluidOnAnyFace_IsUnsafe перестанет ловить эту поломку. При смене canBreak_FluidOnAnyFace_IsUnsafe проверь это утверждение.");
    }

    @Test
    void canBreak_LavaOnDiagonalNeighbor_IsSafe() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID).set(1, 1, 0, Cell.LAVA);

        BreakSafety safety = MiningSafety.canBreak(new BlockCoord(0, 0, 0), world);

        assertTrue(
                safety instanceof BreakSafety.Safe,
                "Жидкость по диагонали не касается грани блока: запрещать ломку нельзя, иначе безопасные "
                        + "блоки рядом с водой остаются недобытыми"
                        + " При смене canBreak_LavaOnDiagonalNeighbor_IsSafe проверь это утверждение.");
    }

    @Test
    void canBreak_NoFluidAround_IsSafe() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID);

        assertTrue(
                MiningSafety.canBreak(new BlockCoord(0, 0, 0), world) instanceof BreakSafety.Safe,
                "Блок без жидкости вокруг безопасен: обычная руда обязана добываться"
                        + " Иначе тест canBreak_NoFluidAround_IsSafe перестанет ловить эту поломку. При смене canBreak_NoFluidAround_IsSafe проверь это утверждение.");
    }

    @Test
    void canBreak_AnyTarget_ChecksOnlySixFaces() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID);

        MiningSafety.canBreak(new BlockCoord(0, 0, 0), world);

        assertEquals(
                6,
                world.reads(),
                "Ломка читает ровно 6 граней (без диагоналей): каждое лишнее чтение — нагрузка на серверный тик"
                        + " Иначе тест canBreak_AnyTarget_ChecksOnlySixFaces перестанет ловить эту поломку. При смене canBreak_AnyTarget_ChecksOnlySixFaces проверь это утверждение.");
    }

    @Test
    void canStand_SolidFloorAndClearColumn_IsAccepted() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID);

        assertTrue(
                MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Твёрдый пол и свободные ноги с головой — годная стоянка: без неё дух не может подойти к цели"
                        + " Иначе тест canStand_SolidFloorAndClearColumn_IsAccepted перестанет ловить эту поломку. При смене canStand_SolidFloorAndClearColumn_IsAccepted проверь это утверждение.");
    }

    @Test
    void canStand_FallingFloor_IsAccepted() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.FALLING);

        assertTrue(
                MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Сыпучий пол (гравий, песок) — обычная опора, как в игре: сыпучее опасно только над головой. "
                        + "Иначе стоянки плана на гравии площади недостижимы и подходов вдвое больше плана"
                        + " При смене canStand_FallingFloor_IsAccepted проверь это утверждение.");
    }

    @Test
    void canStandOnGround_FloatingFloorOverChasm_IsRejected() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID);

        assertFalse(
                MiningSafety.canStandOnGround(new BlockCoord(0, 0, 0), world, 2),
                "Стоянка на висящей плите над пустотой (перенос к стоянке y=80 30.10) обязана отвергаться: "
                        + "дух сходит с неё вниз и разбивается, а перенос — аварийный выход, не приговор"
                        + " Иначе тест canStandOnGround_FloatingFloorOverChasm_IsRejected перестанет ловить эту поломку. При смене canStandOnGround_FloatingFloorOverChasm_IsRejected проверь это утверждение.");
    }

    @Test
    void canStandOnGround_SolidGroundBelowFloor_IsAccepted() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID).set(0, -2, 0, Cell.SOLID);

        assertTrue(
                MiningSafety.canStandOnGround(new BlockCoord(0, 0, 0), world, 2),
                "Стоянка с твёрдым полом и опорой под ним — годная: иначе дух не может выбраться из застревания"
                        + " При смене canStandOnGround_SolidGroundBelowFloor_IsAccepted проверь это утверждение.");
    }

    @Test
    void canStand_FloorIsPassable_IsRejected() {
        CellWorld world = new CellWorld();

        assertFalse(
                MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Стоянка без твёрдого пола (под ногами воздух) обязана отвергаться: дух упадёт в полость"
                        + " Иначе тест canStand_FloorIsPassable_IsRejected перестанет ловить эту поломку. При смене canStand_FloorIsPassable_IsRejected проверь это утверждение.");
    }

    @Test
    void canStand_HeadBlocked_IsRejected() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID).set(0, 1, 0, Cell.SOLID);

        assertFalse(
                MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Стоянка с занятой головой обязана отвергаться: дух не помещается в клетку"
                        + " Иначе тест canStand_HeadBlocked_IsRejected перестанет ловить эту поломку. При смене canStand_HeadBlocked_IsRejected проверь это утверждение.");
    }

    @Test
    void canStand_FeetIsHazard_IsRejected() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID).set(0, 0, 0, Cell.HAZARD);

        assertFalse(
                MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Кактус или паутина в клетке ног обязана отвергаться: дух получает урон, стоя на месте"
                        + " Иначе тест canStand_FeetIsHazard_IsRejected перестанет ловить эту поломку. При смене canStand_FeetIsHazard_IsRejected проверь это утверждение.");
    }

    @Test
    void canStand_FloorIsHazard_IsRejected() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.HAZARD);

        assertFalse(
                MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Магма под ногами обязана отвергаться: дух горит, стоя на такой стоянке"
                        + " Иначе тест canStand_FloorIsHazard_IsRejected перестанет ловить эту поломку. При смене canStand_FloorIsHazard_IsRejected проверь это утверждение.");
    }

    @Test
    void canStand_FluidBesideFeet_IsRejected() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID).set(1, 0, 0, Cell.WATER);

        assertFalse(
                MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Вода рядом с ногами обязана отвергаться: течение уносит духа и он теряет цель"
                        + " Иначе тест canStand_FluidBesideFeet_IsRejected перестанет ловить эту поломку. При смене canStand_FluidBesideFeet_IsRejected проверь это утверждение.");
    }

    @Test
    void canStand_FluidBesideFloor_IsRejected() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID).set(1, -1, 0, Cell.LAVA);

        assertFalse(
                MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Лава у уровня пола соседней клетки обязана отвергаться: затечёт под ноги духа"
                        + " Иначе тест canStand_FluidBesideFloor_IsRejected перестанет ловить эту поломку. При смене canStand_FluidBesideFloor_IsRejected проверь это утверждение.");
    }

    @Test
    void canStepFrom_NeighborSameLevelBesideLava_IsRejected() {
        CellWorld world = new CellWorld().set(1, -1, 0, Cell.SOLID).set(2, 0, 0, Cell.LAVA);

        assertFalse(
                MiningSafety.canStepFrom(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0), world),
                "Соседняя клетка того же уровня, не проходящая canStand (рядом лава), обязана запрещать шаг: "
                        + "иначе дух шагает в опасную клетку мимо навигации"
                        + " При смене canStepFrom_NeighborSameLevelBesideLava_IsRejected проверь это утверждение.");
    }

    @Test
    void canStepFrom_NeighborSameLevelWithoutFloor_IsRejected() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID);

        assertFalse(
                MiningSafety.canStepFrom(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0), world),
                "Соседняя клетка без пола обязана запрещать шаг: без проверки пола дух падает в полость"
                        + " Иначе тест canStepFrom_NeighborSameLevelWithoutFloor_IsRejected перестанет ловить эту поломку. При смене canStepFrom_NeighborSameLevelWithoutFloor_IsRejected проверь это утверждение.");
    }

    @Test
    void canStepFrom_SafeNeighborSameLevel_IsAccepted() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID).set(1, -1, 0, Cell.SOLID);

        assertTrue(
                MiningSafety.canStepFrom(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0), world),
                "Безопасная соседняя клетка того же уровня обязана разрешать шаг: иначе предмет не подобрать"
                        + " При смене canStepFrom_SafeNeighborSameLevel_IsAccepted проверь это утверждение.");
    }

    @Test
    void canStepFrom_NeighborOtherLevel_IsRejected() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID).set(1, -2, 0, Cell.SOLID);

        assertFalse(
                MiningSafety.canStepFrom(new BlockCoord(0, 0, 0), new BlockCoord(1, -1, 0), world),
                "Соседняя клетка другого уровня обязана запрещать прямой шаг: предмет на дне ямы недосягаем пешком"
                        + " Иначе тест canStepFrom_NeighborOtherLevel_IsRejected перестанет ловить эту поломку. При смене canStepFrom_NeighborOtherLevel_IsRejected проверь это утверждение.");
    }
}
