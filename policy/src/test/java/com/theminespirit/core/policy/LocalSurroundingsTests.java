package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocalSurroundingsTests {
    @Test
    void read_AnyView_ReadsExactlyTwentySevenCells() {
        AsciiWorld world = AsciiWorld.of(
                Pictures.at(-1, -1, -1), Cell.PASSABLE, Pictures.SOLID_LAYER, Pictures.OPEN_LAYER, Pictures.OPEN_LAYER);

        LocalSurroundings.read(Pictures.FEET, world, BodyFlags.CALM, Optional.empty());

        assertEquals(27, world.reads(), "Снимок читает ровно 27 клеток 3×3×3: это предел яруса 1 стоимости тика духа");
    }

    @Test
    void cellAt_OffsetsInsideSnapshot_ReturnCellsOfTheirLayers() {
        LocalSurroundings surroundings = Pictures.of("###/#H#/###", ".../..L/...", ".../.W./...", BodyFlags.CALM);

        assertEquals(Cell.HAZARD, surroundings.cellAt(0, -1, 0), "слой пола читается со смещением dy = -1");
        assertEquals(Cell.LAVA, surroundings.cellAt(1, 0, 0), "восток читается со смещением dx = +1");
        assertEquals(Cell.WATER, surroundings.cellAt(0, 1, 0), "слой головы читается со смещением dy = +1");
        assertEquals(Cell.SOLID, surroundings.cellAt(-1, -1, 1), "угол пола читается со смещениями (-1, -1, +1)");
    }

    @Test
    void cellAt_CoordinateInsideSnapshot_UsesWorldCoordinates() {
        LocalSurroundings surroundings =
                Pictures.of(Pictures.SOLID_LAYER, ".../.../L..", Pictures.OPEN_LAYER, BodyFlags.CALM);

        assertEquals(
                Cell.LAVA,
                surroundings.cellAt(Pictures.at(-1, 0, 1)),
                "мировая координата переводится в смещение от ног");
    }

    @Test
    void cellAt_CoordinateOutsideSnapshot_IsUnseenPassable() {
        LocalSurroundings surroundings =
                Pictures.of(Pictures.SOLID_LAYER, "LLL/LLL/LLL", Pictures.OPEN_LAYER, BodyFlags.CALM);

        assertEquals(
                Cell.PASSABLE,
                surroundings.cellAt(new BlockCoord(Pictures.FEET.x() + 2, Pictures.FEET.y(), Pictures.FEET.z())),
                "Невидимая за снимком клетка считается безопасной: рефлекс решает по тому, что видит, а следующий тик увидит дальше");
    }

    @Test
    void cellAt_OffsetOutsideSnapshot_ThrowsWithTheOffset() {
        LocalSurroundings surroundings =
                Pictures.of(Pictures.SOLID_LAYER, Pictures.OPEN_LAYER, Pictures.OPEN_LAYER, BodyFlags.CALM);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> surroundings.cellAt(2, 0, 0),
                "смещение за пределами 3×3×3 — ошибка вызывающего, а не молчаливое значение");

        assertEquals(
                "Смещение (2, 0, 0) вне снимка 3×3×3: допустимы значения от -1 до 1",
                error.getMessage(),
                "ошибка называет смещение и допустимый диапазон");
    }

    @Test
    void read_ViewReturnsNull_ThrowsNullPointer() {
        assertThrows(
                NullPointerException.class,
                () -> LocalSurroundings.read(Pictures.FEET, coord -> null, BodyFlags.CALM, Optional.empty()),
                "классификатор, вернувший null, ломает рефлекс позже и непонятнее: ошибка должна быть при чтении снимка");
    }
}
