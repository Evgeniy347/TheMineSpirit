package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AscendRoomTests {
    @Test
    void blocked_ClearCellAndHeadroom_IsFalse() {
        assertFalse(
                AscendRoom.blocked(false, false),
                "Подъём в свободную клетку с чистым местом над головой разрешён даже при потолке над текущей "
                        + "позицией: иначе дух отказывается от шага WALK в -172,72,-488 по лестнице шахты и "
                        + "ходит вниз-вверх (окно 38, spirit-6, «шаг перекрыт» на каждой ступени). При смене "
                        + "проверки подъёма глянь живой подъём по лестнице: цель и клетка над ней свободны");
    }

    @Test
    void blocked_TargetCellSolid_IsTrue() {
        assertTrue(
                AscendRoom.blocked(true, false),
                "Занятая клетка цели запрещает подъём: иначе дух прыгает в камень и топчется 60 тиков "
                        + "(окно 32, spirit-1 у -171,71,-489, цель -170,72,-489). При смене проверки подъёма "
                        + "проверь живой прыжок под потолком: отказ сразу, обход с копанием");
    }

    @Test
    void blocked_BlockAboveTarget_IsTrue() {
        assertTrue(
                AscendRoom.blocked(false, true),
                "Блок над клеткой цели запрещает подъём: дух не сможет встать в клетку высотой один блок, "
                        + "иначе прыгает под потолком на 0.42 и толчётся 60 тиков. При смене проверки подъёма "
                        + "проверь живой прыжок под потолком: отказ сразу, обход с копанием");
    }
}
