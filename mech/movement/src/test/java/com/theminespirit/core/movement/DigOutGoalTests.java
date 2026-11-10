package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class DigOutGoalTests {
    private static final BlockCoord HOME = new BlockCoord(-171, 72, -497);

    @Test
    void ascent_AtHome_IsReached() {
        assertTrue(DigOutGoal.ascent(HOME, HOME, 2), "Дух в точке дома достиг цели выхода: дальше копать нечего");
    }

    @Test
    void ascent_AboveHomeButFarAway_IsNotReached() {
        assertFalse(
                DigOutGoal.ascent(new BlockCoord(HOME.x() + 5, HOME.y() + 4, HOME.z()), HOME, 2),
                "Высота дома не делает выходом клетку в стороне: живой spirit-6 объявил выход, забравшись выше "
                        + "дома по горизонтали, и walk(home) шёл по крыше");
    }

    @Test
    void ascent_BelowHomeAtSameColumn_IsNotReached() {
        assertFalse(
                DigOutGoal.ascent(new BlockCoord(HOME.x(), HOME.y() - 1, HOME.z()), HOME, 2),
                "Дойти по x/z недостаточно: дух ещё ниже дома, выход не закончен");
    }

    @Test
    void ascent_RadiusEdge_IsReached() {
        assertTrue(
                DigOutGoal.ascent(new BlockCoord(HOME.x() - 2, HOME.y(), HOME.z() + 2), HOME, 2),
                "Клетка на краю радиуса дома — ещё цель dig_out: тот же радиус, что у маршрута");
    }

    @Test
    void ascent_OneOutsideRadius_IsNotReached() {
        assertFalse(
                DigOutGoal.ascent(new BlockCoord(HOME.x() + 3, HOME.y(), HOME.z()), HOME, 2),
                "За радиусом дома выход не закончен, даже если дух уже на его высоте");
    }
}
