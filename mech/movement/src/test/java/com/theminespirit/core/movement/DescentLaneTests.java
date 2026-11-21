package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DescentLaneTests {
    private static final BlockCoord FEET = new BlockCoord(0, 70, 0);

    private static DescentLane.Coverage solidAt(BlockCoord... cells) {
        Set<BlockCoord> solid = Set.of(cells);
        return solid::contains;
    }

    @Test
    void blocked_ThreeBelowClearOverFloor_IsFalse() {
        boolean blocked = DescentLane.blocked(
                FEET, new BlockCoord(1, 67, 0), solidAt(new BlockCoord(0, 69, 0), new BlockCoord(1, 69, 0)));

        assertFalse(
                blocked,
                "Спуск с уступа на три блока при чистой клетке над целью — свободен: иначе пол под ногами "
                        + "попадает на линию, дух отказывается от каждого спуска (окно 33: spirit-1 70→67, "
                        + "spirit-6 75→72, spirit-5 70→67, по 2–3 отказа подряд), маршрут сходит и строится "
                        + "заново по кругу. При смене геометрии проверь окно 31: настоящий бортик над целью "
                        + "обязан оставаться перекрытием");
    }

    @Test
    void blocked_BlockAboveCell_IsTrue() {
        boolean blocked = DescentLane.blocked(FEET, new BlockCoord(1, 69, 0), solidAt(new BlockCoord(1, 70, 0)));

        assertTrue(
                blocked,
                "Блок на уровне головы над клеткой спуска — перекрытие: иначе дух прыгает вниз и толкается "
                        + "в бортик 60 тиков (окно 31, дух у -171,73,-489, между=блок). При смене геометрии "
                        + "проверь живой сход с уступа: бортик над целью отвергается сразу");
    }

    @Test
    void blocked_TargetCellBlocked_IsTrue() {
        boolean blocked = DescentLane.blocked(FEET, new BlockCoord(1, 67, 0), solidAt(new BlockCoord(1, 67, 0)));

        assertTrue(
                blocked,
                "Занятая клетка назначения — перекрытие: иначе дух прыгает в камень и топчется 60 тиков. "
                        + "При смене геометрии проверь спуск в занятую клетку: отказ сразу, обход с копанием");
    }

    @Test
    void blocked_FloorAtFeetLevel_IsFalse() {
        boolean blocked = DescentLane.blocked(FEET, new BlockCoord(1, 69, 0), solidAt(new BlockCoord(0, 69, 0)));

        assertFalse(
                blocked,
                "Спуск на один блок прямо в соседнюю клетку над полом — свободен: иначе каждый сход со "
                        + "ступени объявлялся бы перекрытым, а шаг вниз на 1 решается отдельно в WalkAscent "
                        + "(MOVE). При смене геометрии проверь спуск в яму dig_out: шаг на -1 остаётся MOVE");
    }
}
