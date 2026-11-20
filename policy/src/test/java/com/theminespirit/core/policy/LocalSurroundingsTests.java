package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocalSurroundingsTests {
    @Test
    void read_AnyView_ReadsExactlyTwentySevenCells() {
        AsciiWorld world = AsciiWorld.of(
                Pictures.at(-1, -1, -1), Cell.PASSABLE, Pictures.SOLID_LAYER, Pictures.OPEN_LAYER, Pictures.OPEN_LAYER);

        LocalSurroundings.read(Pictures.FEET, world, BodyFlags.CALM, Optional.empty());

        assertEquals(
                27,
                world.reads(),
                "Снимок читает ровно 27 клеток 3×3×3: это предел яруса 1 стоимости тика духа, лишнее чтение "
                        + "тормозит тик и складывается по всем духам поселения, иначе сервер проседает. "
                        + "При смене read проверь, что клетки не читаются дважды");
    }

    @Test
    void cellAt_OffsetsInsideSnapshot_ReturnCellsOfTheirLayers() {
        LocalSurroundings surroundings = Pictures.of("###/#H#/###", ".../..L/...", ".../.W./...", BodyFlags.CALM);

        assertEquals(
                Cell.HAZARD,
                surroundings.cellAt(0, -1, 0),
                "слой пола читается со смещением dy = -1, иначе рефлекс смотрит не под ноги. "
                        + "При смене осей проверь все три смещения по отдельности");
        assertEquals(
                Cell.LAVA,
                surroundings.cellAt(1, 0, 0),
                "восток читается со смещением dx = +1, иначе приоритет бегства выбирает не ту сторону. "
                        + "При смене осей проверь знак смещения");
        assertEquals(
                Cell.WATER,
                surroundings.cellAt(0, 1, 0),
                "слой головы читается со смещением dy = +1, иначе дух не замечает воду над головой и тонет "
                        + "с полным запасом снимка. При смене осей проверь верхний слой");
        assertEquals(
                Cell.SOLID,
                surroundings.cellAt(-1, -1, 1),
                "угол пола читается со смещениями (-1, -1, +1), иначе угловые опасности теряются. "
                        + "При смене индексации проверь диагональную клетку");
    }

    @Test
    void cellAt_CoordinateInsideSnapshot_UsesWorldCoordinates() {
        LocalSurroundings surroundings =
                Pictures.of(Pictures.SOLID_LAYER, ".../.../L..", Pictures.OPEN_LAYER, BodyFlags.CALM);

        assertEquals(
                Cell.LAVA,
                surroundings.cellAt(Pictures.at(-1, 0, 1)),
                "мировая координата переводится в смещение от ног, иначе чтения по миру и по смещению дают "
                        + "разные клетки и рефлекс видит не ту опасность. При смене cellAt проверь клетку пола "
                        + "по мировым координатам, а не только по смещениям");
    }

    @Test
    void cellAt_CoordinateOutsideSnapshot_IsUnseenPassable() {
        LocalSurroundings surroundings =
                Pictures.of(Pictures.SOLID_LAYER, "LLL/LLL/LLL", Pictures.OPEN_LAYER, BodyFlags.CALM);

        assertEquals(
                Cell.PASSABLE,
                surroundings.cellAt(new BlockCoord(Pictures.FEET.x() + 2, Pictures.FEET.y(), Pictures.FEET.z())),
                "Невидимая за снимком клетка считается безопасной: рефлекс решает по тому, что видит, а следующий "
                        + "тик увидит дальше, иначе рефлекс замрёт на невидимом. При смене cellAt проверь клетку на расстоянии +2 от ног");
    }

    @Test
    void cellAt_OffsetOutsideSnapshot_ThrowsWithTheOffset() {
        LocalSurroundings surroundings =
                Pictures.of(Pictures.SOLID_LAYER, Pictures.OPEN_LAYER, Pictures.OPEN_LAYER, BodyFlags.CALM);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> surroundings.cellAt(2, 0, 0),
                "смещение за пределами 3×3×3 — ошибка вызывающего, а не молчаливое значение, иначе рефлекс "
                        + "читает соседнюю клетку вместо своей. При смене границ проверь край 1 и 2");

        assertEquals(
                "Смещение (2, 0, 0) вне снимка 3×3×3: допустимы значения от -1 до 1",
                error.getMessage(),
                "ошибка называет смещение и допустимый диапазон, иначе причину отказа не найти по логу; "
                        + "при смене текста проверь, что смещение и границы остаются");
    }

    @Test
    void read_ViewReturnsNull_ThrowsNullPointer() {
        assertThrows(
                NullPointerException.class,
                () -> LocalSurroundings.read(Pictures.FEET, coord -> null, BodyFlags.CALM, Optional.empty()),
                "классификатор, вернувший null, ломает рефлекс позже и непонятнее: ошибка должна быть при чтении "
                        + "снимка. При смене read проверь, что null не проскакивает в снимок");
    }
}
