package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertTrue(safety instanceof BreakSafety.Unsafe,
                "Жидкость на грани " + fluid + " (" + dx + "," + dy + "," + dz + ") обязана запрещать ломку: "
                        + "затекшая в полость жидкость смывает духа или убивает его лавой");
    }

    @Test
    void canBreak_LavaOnDiagonalNeighbor_IsSafe() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID).set(1, 1, 0, Cell.LAVA);

        BreakSafety safety = MiningSafety.canBreak(new BlockCoord(0, 0, 0), world);

        assertTrue(safety instanceof BreakSafety.Safe,
                "Жидкость по диагонали не касается грани блока: запрещать ломку нельзя, иначе безопасные "
                        + "блоки рядом с водой остаются недобытыми");
    }

    @Test
    void canBreak_NoFluidAround_IsSafe() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID);

        assertTrue(MiningSafety.canBreak(new BlockCoord(0, 0, 0), world) instanceof BreakSafety.Safe,
                "Блок без жидкости вокруг безопасен: обычная руда обязана добываться");
    }

    @Test
    void canBreak_ChecksOnlySixFaces() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID);

        MiningSafety.canBreak(new BlockCoord(0, 0, 0), world);

        assertEquals(MiningSafety.FACE_READS, world.reads(),
                "Ломка читает ровно 6 граней (без диагоналей): каждое лишнее чтение — нагрузка на серверный тик");
    }

    @Test
    void canStand_SolidFloorAndClearColumn_IsAccepted() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID);

        assertTrue(MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Твёрдый пол и свободные ноги с головой — годная стоянка: без неё дух не может подойти к цели");
    }

    @Test
    void canStand_FloorIsPassable_IsRejected() {
        CellWorld world = new CellWorld();

        assertFalse(MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Стоянка без твёрдого пола (под ногами воздух) обязана отвергаться: дух упадёт в полость");
    }

    @Test
    void canStand_HeadBlocked_IsRejected() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID).set(0, 1, 0, Cell.SOLID);

        assertFalse(MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Стоянка с занятой головой обязана отвергаться: дух не помещается в клетку");
    }

    @Test
    void canStand_FeetIsHazard_IsRejected() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID).set(0, 0, 0, Cell.HAZARD);

        assertFalse(MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Кактус или паутина в клетке ног обязана отвергаться: дух получает урон, стоя на месте");
    }

    @Test
    void canStand_FloorIsHazard_IsRejected() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.HAZARD);

        assertFalse(MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Магма под ногами обязана отвергаться: дух горит, стоя на такой стоянке");
    }

    @Test
    void canStand_FluidBesideFeet_IsRejected() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID).set(1, 0, 0, Cell.WATER);

        assertFalse(MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Вода рядом с ногами обязана отвергаться: течение уносит духа и он теряет цель");
    }

    @Test
    void canStand_FluidBesideFloor_IsRejected() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID).set(1, -1, 0, Cell.LAVA);

        assertFalse(MiningSafety.canStand(new BlockCoord(0, 0, 0), world),
                "Лава у уровня пола соседней клетки обязана отвергаться: затечёт под ноги духа");
    }
}
